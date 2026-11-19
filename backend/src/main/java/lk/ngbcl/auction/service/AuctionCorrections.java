package lk.ngbcl.auction.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.auction.domain.Auction;
import lk.ngbcl.auction.domain.AuctionLot;
import lk.ngbcl.auction.domain.AuctionSettings;
import lk.ngbcl.auction.domain.AuctionStatus;
import lk.ngbcl.auction.domain.AuctionTransaction;
import lk.ngbcl.auction.domain.AuctionTransactionRepository;
import lk.ngbcl.auction.domain.BudgetAdjustment;
import lk.ngbcl.auction.domain.BudgetAdjustmentRepository;
import lk.ngbcl.auction.domain.LotState;
import lk.ngbcl.auction.domain.TeamBudget;
import lk.ngbcl.auction.domain.TeamBudgetRepository;
import lk.ngbcl.auction.domain.TeamPlayer;
import lk.ngbcl.auction.domain.TeamPlayerRepository;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.shared.web.ApiException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fixes after a sale (AUC-025..027): release a player, move a sale to another team, adjust a
 * purse. Every correction needs a reason, is audited, keeps the original transaction (marked
 * REVERSED) and runs under the same auction lock as live commands.
 */
@Service
public class AuctionCorrections {

    private final AuctionEngine engine;
    private final AuctionTransactionRepository transactions;
    private final TeamBudgetRepository budgets;
    private final TeamPlayerRepository teamPlayers;
    private final BudgetAdjustmentRepository adjustments;
    private final AuctionSettingsService settingsService;
    private final AuditService audit;
    private final AuctionEvents events;
    private final Clock clock;

    public AuctionCorrections(AuctionEngine engine, AuctionTransactionRepository transactions,
            TeamBudgetRepository budgets, TeamPlayerRepository teamPlayers, BudgetAdjustmentRepository adjustments,
            AuctionSettingsService settingsService, AuditService audit, AuctionEvents events, Clock clock) {
        this.engine = engine;
        this.transactions = transactions;
        this.budgets = budgets;
        this.teamPlayers = teamPlayers;
        this.adjustments = adjustments;
        this.settingsService = settingsService;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Reverses a sale: refund, release from the squad, player becomes unsold (re-auctionable). */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CORRECT')")
    public void releaseSale(UUID lotId, String reason) {
        String why = requireReason(reason);
        Auction a = engine.lock();
        AuctionLot lot = soldLot(a, lotId);
        AuctionTransaction txn = undoSale(a, lot, why);
        lot.reverseSale(clock.instant());
        audit.record(AuctionEventLog.of("auction.sale_released", a).withReason(why)
                .withChange(AuctionEngine.mapOf("teamId", txn.getTeamId(), "price", txn.getAmount(), "lot",
                        lot.getLotNumber()), null));
        events.publish(a, "CORRECTION");
    }

    /** Moves a sale to another team, at the same or a corrected price. */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CORRECT')")
    public void moveSale(UUID lotId, UUID toTeamId, Long price, String reason) {
        String why = requireReason(reason);
        Auction a = engine.lock();
        AuctionLot lot = soldLot(a, lotId);
        long newPrice = price == null ? lot.getFinalPrice() : price;
        if (newPrice <= 0) {
            throw ApiException.unprocessable("INVALID_PRICE", "Enter a price above zero.");
        }
        TeamBudget target = budgets.findByAuctionIdAndTeamId(a.getId(), toTeamId)
                .orElseThrow(() -> ApiException.unprocessable("TEAM_NOT_IN_AUCTION", "That team isn't in the auction."));
        AuctionSettings s = settingsService.current(a.getTournamentId());
        boolean sameTeam = toTeamId.equals(lot.getWinningTeamId());
        if (!sameTeam && target.getSquadCount() >= s.getMaxSquadSize()) {
            throw ApiException.unprocessable("SQUAD_FULL", "That team's squad is full.");
        }
        AuctionTransaction old = undoSale(a, lot, why);
        Instant now = clock.instant();
        target.buy(newPrice);
        AuctionTransaction txn = transactions.save(new AuctionTransaction(a.getId(), lot.getId(), toTeamId,
                lot.getTournamentPlayerId(), newPrice, CurrentUser.get().map(CurrentUser.Actor::id).orElse(null), now));
        teamPlayers.save(new TeamPlayer(a.getTournamentId(), toTeamId, lot.getTournamentPlayerId(), "AUCTION", newPrice,
                txn.getId(), lot.getLotNumber()));
        lot.reassign(toTeamId, newPrice);
        teamPlayers.flush();
        audit.record(AuctionEventLog.of("auction.sale_moved", a).withReason(why)
                .withChange(AuctionEngine.mapOf("teamId", old.getTeamId(), "price", old.getAmount()),
                        AuctionEngine.mapOf("teamId", toTeamId, "price", newPrice)));
        events.publish(a, "CORRECTION");
    }

    /** Adds to (positive) or takes from (negative) a team's purse, if the settings allow it. */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CORRECT')")
    public void adjustBudget(UUID teamId, long amount, String reason) {
        String why = requireReason(reason);
        Auction a = engine.lock();
        if (a.getStatus() == AuctionStatus.NOT_STARTED) {
            throw ApiException.conflict("WRONG_STATE", "Budgets are created when the auction starts.");
        }
        if (!settingsService.current(a.getTournamentId()).isAllowManualAdjustment()) {
            throw ApiException.conflict("ADJUSTMENT_DISABLED", "Manual budget adjustments are turned off in the auction settings.");
        }
        if (amount == 0) {
            throw ApiException.unprocessable("INVALID_AMOUNT", "Enter an amount to add or remove.");
        }
        TeamBudget b = budgets.findByAuctionIdAndTeamId(a.getId(), teamId)
                .orElseThrow(() -> ApiException.notFound("Team budget"));
        long before = b.getRemaining();
        b.adjust(amount);
        adjustments.save(new BudgetAdjustment(b.getId(), amount, why,
                CurrentUser.get().map(CurrentUser.Actor::id).orElse(null), clock.instant()));
        audit.record(AuctionEventLog.of("auction.budget_adjusted", a).withReason(why)
                .withChange(AuctionEngine.mapOf("teamId", teamId, "remaining", before),
                        AuctionEngine.mapOf("teamId", teamId, "remaining", b.getRemaining(), "amount", amount)));
        events.publish(a, "CORRECTION");
    }

    private AuctionLot soldLot(Auction a, UUID lotId) {
        AuctionLot lot = engine.ownLot(a, lotId);
        if (lot.getState() != LotState.SOLD) {
            throw ApiException.conflict("NOT_SOLD", "Only sold players can be corrected.");
        }
        return lot;
    }

    private AuctionTransaction undoSale(Auction a, AuctionLot lot, String why) {
        Instant now = clock.instant();
        UUID actor = CurrentUser.get().map(CurrentUser.Actor::id).orElse(null);
        AuctionTransaction txn = transactions.findByLotIdAndStatus(lot.getId(), "SOLD")
                .orElseThrow(() -> ApiException.conflict("NOT_SOLD", "No active sale found for this player."));
        txn.reverse(actor, now, why);
        budgets.findByAuctionIdAndTeamId(a.getId(), txn.getTeamId()).orElseThrow().refund(txn.getAmount());
        teamPlayers.findByTournamentIdAndTournamentPlayerIdAndActiveTrue(a.getTournamentId(), lot.getTournamentPlayerId())
                .ifPresent(tp -> tp.release(now, why));
        // The partial unique indexes (one SOLD txn per lot, one active team per player) must see
        // the old rows released before the replacement rows are inserted.
        transactions.flush();
        teamPlayers.flush();
        return txn;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.unprocessable("REASON_REQUIRED", "Give a reason — corrections are audited.");
        }
        if (reason.length() > 300) {
            throw ApiException.unprocessable("REASON_TOO_LONG", "Keep the reason under 300 characters.");
        }
        return reason.trim();
    }
}
