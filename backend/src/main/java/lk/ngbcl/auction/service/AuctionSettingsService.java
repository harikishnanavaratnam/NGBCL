package lk.ngbcl.auction.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.auction.domain.Auction;
import lk.ngbcl.auction.domain.AuctionRepository;
import lk.ngbcl.auction.domain.AuctionSettings;
import lk.ngbcl.auction.domain.AuctionSettingsRepository;
import lk.ngbcl.auction.domain.AuctionStatus;
import lk.ngbcl.auction.domain.TeamBudget;
import lk.ngbcl.auction.domain.TeamBudgetRepository;
import lk.ngbcl.auction.service.AuctionDtos.SettingsView;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.FieldErrors;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auction rules (AUC-002..008). Before the auction starts every value can change. Once it has
 * started they are locked: changes need a reason, are audited, apply from the next lot, and
 * the initial budget can no longer change (use a budget adjustment instead).
 */
@Service
public class AuctionSettingsService {

    private final AuctionSettingsRepository settings;
    private final AuctionRepository auctions;
    private final TeamBudgetRepository budgets;
    private final TournamentService tournament;
    private final AuditService audit;
    private final AuctionEvents events;

    public AuctionSettingsService(AuctionSettingsRepository settings, AuctionRepository auctions,
            TeamBudgetRepository budgets, TournamentService tournament, AuditService audit, AuctionEvents events) {
        this.settings = settings;
        this.auctions = auctions;
        this.budgets = budgets;
        this.tournament = tournament;
        this.audit = audit;
        this.events = events;
    }

    public record SettingsInput(long initialBudget, int maxSquadSize, Integer minSquadSize, long defaultBasePrice,
            long minIncrement, boolean allowReauction, boolean allowManualAdjustment, Instant startsAt, Long version,
            String reason) {
    }

    @Transactional
    public AuctionSettings current(UUID tournamentId) {
        return settings.findById(tournamentId).orElseGet(() -> settings.saveAndFlush(new AuctionSettings(tournamentId)));
    }

    /** Read-only callers: the saved settings, or the defaults if none were saved yet. */
    @Transactional(readOnly = true)
    public AuctionSettings peek(UUID tournamentId) {
        return settings.findById(tournamentId).orElseGet(() -> new AuctionSettings(tournamentId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('AUCTION_CONFIGURE', 'AUCTION_CONTROL')")
    public SettingsView get() {
        UUID t = tournament.currentTournamentId();
        return view(peek(t), started(t));
    }

    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONFIGURE')")
    public SettingsView update(SettingsInput in) {
        UUID t = tournament.currentTournamentId();
        AuctionSettings s = current(t);
        if (in.version() != null && !Objects.equals(s.getVersion(), in.version())) {
            throw ApiException.conflict("STALE_DATA", "Someone else saved the auction settings. Reload to see their changes.");
        }
        Auction auction = auctions.lockByTournamentId(t).orElse(null);
        boolean locked = auction != null && auction.getStatus() != AuctionStatus.NOT_STARTED;

        FieldErrors e = new FieldErrors();
        if (in.initialBudget() <= 0) {
            e.add("initialBudget", "Enter the budget each team starts with.");
        }
        if (in.maxSquadSize() < 1 || in.maxSquadSize() > 40) {
            e.add("maxSquadSize", "Squad size must be between 1 and 40.");
        }
        if (in.minSquadSize() != null && (in.minSquadSize() < 1 || in.minSquadSize() > in.maxSquadSize())) {
            e.add("minSquadSize", "Minimum squad must be between 1 and the maximum squad size.");
        }
        if (in.defaultBasePrice() <= 0) {
            e.add("defaultBasePrice", "Enter the default base price.");
        } else if (in.defaultBasePrice() > in.initialBudget()) {
            e.add("defaultBasePrice", "The base price can't be more than a team's budget.");
        }
        if (in.minIncrement() <= 0) {
            e.add("minIncrement", "Enter the minimum bid increment.");
        }
        if (locked) {
            if (in.initialBudget() != s.getInitialBudget()) {
                e.add("initialBudget", "Locked once the auction starts. Use a budget adjustment instead.");
            }
            if (in.reason() == null || in.reason().isBlank()) {
                e.add("reason", "Give a reason — changes after the auction starts are audited.");
            }
            int biggestSquad = budgets.findByAuctionId(auction.getId()).stream().mapToInt(TeamBudget::getSquadCount)
                    .max().orElse(0);
            if (in.maxSquadSize() < biggestSquad) {
                e.add("maxSquadSize", "A team already has " + biggestSquad + " players.");
            }
        }
        e.throwIfAny();

        Map<String, Object> before = snapshot(s);
        s.update(in.initialBudget(), in.maxSquadSize(), in.minSquadSize(), in.defaultBasePrice(), in.minIncrement(),
                in.allowReauction(), in.allowManualAdjustment(), in.startsAt());
        settings.flush();
        if (locked) {
            auction.updateSnapshot(snapshot(s));
            events.publish(auction, "SETTINGS_CHANGED");
        }
        audit.record(AuditEvent.of(locked ? "auction.settings.changed_after_start" : "auction.settings.updated",
                "AuctionSettings", t).inTournament(t).withChange(before, snapshot(s))
                .withReason(locked ? in.reason().trim() : null));
        return view(s, locked);
    }

    boolean started(UUID tournamentId) {
        return auctions.findByTournamentId(tournamentId).map(a -> a.getStatus() != AuctionStatus.NOT_STARTED)
                .orElse(false);
    }

    static Map<String, Object> snapshot(AuctionSettings s) {
        Map<String, Object> m = new HashMap<>();
        m.put("initialBudget", s.getInitialBudget());
        m.put("maxSquadSize", s.getMaxSquadSize());
        m.put("minSquadSize", s.getMinSquadSize());
        m.put("defaultBasePrice", s.getDefaultBasePrice());
        m.put("minIncrement", s.getMinIncrement());
        m.put("allowReauction", s.isAllowReauction());
        m.put("allowManualAdjustment", s.isAllowManualAdjustment());
        return m;
    }

    static SettingsView view(AuctionSettings s, boolean locked) {
        return new SettingsView(s.getInitialBudget(), s.getMaxSquadSize(), s.getMinSquadSize(), s.getDefaultBasePrice(),
                s.getMinIncrement(), s.isAllowReauction(), s.isAllowManualAdjustment(), s.getStartsAt(), locked,
                s.getVersion());
    }
}
