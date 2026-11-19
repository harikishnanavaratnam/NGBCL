package lk.ngbcl.auction.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.auction.domain.Auction;
import lk.ngbcl.auction.domain.AuctionBid;
import lk.ngbcl.auction.domain.AuctionBidRepository;
import lk.ngbcl.auction.domain.AuctionLot;
import lk.ngbcl.auction.domain.AuctionLotRepository;
import lk.ngbcl.auction.domain.AuctionRepository;
import lk.ngbcl.auction.domain.AuctionSettings;
import lk.ngbcl.auction.domain.AuctionStatus;
import lk.ngbcl.auction.domain.AuctionTransaction;
import lk.ngbcl.auction.domain.AuctionTransactionRepository;
import lk.ngbcl.auction.domain.BidStatus;
import lk.ngbcl.auction.domain.CallState;
import lk.ngbcl.auction.domain.LotState;
import lk.ngbcl.auction.domain.TeamBudget;
import lk.ngbcl.auction.domain.TeamBudgetRepository;
import lk.ngbcl.auction.domain.TeamPlayer;
import lk.ngbcl.auction.domain.TeamPlayerRepository;
import lk.ngbcl.player.service.PlayerService;
import lk.ngbcl.player.service.PlayerService.LotPlayer;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.shared.domain.Ids;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.team.service.TeamService;
import lk.ngbcl.team.service.TeamService.TeamCard;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every command that changes the auction. Each one locks the edition's auction row first, so
 * commands run one at a time, see the latest state, and bump the state version exactly once
 * (SRS §8.3). Money moves only inside these transactions; DB CHECKs are the last line of defence.
 */
@Service
public class AuctionEngine {

    private final AuctionRepository auctions;
    private final AuctionLotRepository lots;
    private final AuctionBidRepository bids;
    private final TeamBudgetRepository budgets;
    private final AuctionTransactionRepository transactions;
    private final TeamPlayerRepository teamPlayers;
    private final AuctionSettingsService settingsService;
    private final TournamentService tournament;
    private final PlayerService players;
    private final TeamService teams;
    private final AuditService audit;
    private final AuctionEvents events;
    private final Clock clock;

    public AuctionEngine(AuctionRepository auctions, AuctionLotRepository lots, AuctionBidRepository bids,
            TeamBudgetRepository budgets, AuctionTransactionRepository transactions, TeamPlayerRepository teamPlayers,
            AuctionSettingsService settingsService, TournamentService tournament, PlayerService players,
            TeamService teams, AuditService audit, AuctionEvents events, Clock clock) {
        this.auctions = auctions;
        this.lots = lots;
        this.bids = bids;
        this.budgets = budgets;
        this.transactions = transactions;
        this.teamPlayers = teamPlayers;
        this.settingsService = settingsService;
        this.tournament = tournament;
        this.players = players;
        this.teams = teams;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record BidCommand(UUID lotId, UUID teamId, long amount, String idempotencyKey) {
    }

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record BidResult(UUID bidId, UUID lotId, UUID teamId, long amount, int bidNumber, long stateVersion,
            boolean replayed) {
    }

    // ---- Preparation: the queue follows the approved player list ----------------------------

    /**
     * Creates the auction row if needed and brings the queue in line with approved players:
     * new approved players are queued, players no longer eligible leave the queue, queued base
     * prices follow the default/override. Publishes only when something actually changed.
     */
    @Transactional
    @PreAuthorize("hasAnyAuthority('AUCTION_CONFIGURE', 'AUCTION_CONTROL', 'AUCTION_CORRECT')")
    public void prepare() {
        UUID t = tournament.currentTournamentId();
        // Never read the row before locking it: a copy loaded earlier in this transaction
        // would not be refreshed by the locking query.
        auctions.insertIfMissing(Ids.newId(), t);
        Auction a = auctions.lockByTournamentId(t).orElseThrow();
        if (a.getStatus() == AuctionStatus.COMPLETED) {
            return;
        }
        if (syncQueue(a) | syncBudgets(a)) {
            events.publish(a, "QUEUE_CHANGED");
        }
    }

    private boolean syncQueue(Auction a) {
        UUID t = a.getTournamentId();
        AuctionSettings s = settingsService.current(t);
        Set<UUID> allocated = teamPlayers.findByTournamentIdAndActiveTrue(t).stream()
                .map(TeamPlayer::getTournamentPlayerId).collect(Collectors.toSet());
        Map<UUID, LotPlayer> eligible = new HashMap<>();
        players.approvedEntries(t).stream().filter(p -> !allocated.contains(p.entryId()))
                .forEach(p -> eligible.put(p.entryId(), p));

        List<AuctionLot> existing = lots.findByAuctionIdOrderByRoundAscQueueOrderAsc(a.getId());
        Map<UUID, AuctionLot> byEntry = existing.stream()
                .collect(Collectors.toMap(AuctionLot::getTournamentPlayerId, Function.identity()));
        boolean changed = false;
        Instant now = clock.instant();

        for (AuctionLot l : existing) {
            LotPlayer p = eligible.get(l.getTournamentPlayerId());
            boolean waiting = l.getState() == LotState.QUEUED || l.getState() == LotState.UNSOLD;
            if (waiting && p == null) {
                l.close(LotState.WITHDRAWN, now);
                changed = true;
            } else if (l.getState() == LotState.QUEUED) {
                long base = p.basePriceOverride() != null ? p.basePriceOverride() : s.getDefaultBasePrice();
                if (base != l.getBasePrice()) {
                    l.setBasePrice(base);
                    changed = true;
                }
            }
        }
        int order = lots.maxOrder(a.getId());
        List<LotPlayer> fresh = eligible.values().stream().filter(p -> !byEntry.containsKey(p.entryId()))
                .sorted((x, y) -> String.CASE_INSENSITIVE_ORDER.compare(x.fullName(), y.fullName())).toList();
        for (LotPlayer p : fresh) {
            long base = p.basePriceOverride() != null ? p.basePriceOverride() : s.getDefaultBasePrice();
            lots.save(new AuctionLot(a.getId(), p.entryId(), base, a.getCurrentRound(), ++order));
            changed = true;
        }
        if (changed) {
            lots.flush();
        }
        return changed;
    }

    /** Teams activated after the auction started get a purse too. */
    private boolean syncBudgets(Auction a) {
        if (a.getStatus() == AuctionStatus.NOT_STARTED) {
            return false;
        }
        AuctionSettings s = settingsService.current(a.getTournamentId());
        Set<UUID> have = budgets.findByAuctionId(a.getId()).stream().map(TeamBudget::getTeamId).collect(Collectors.toSet());
        boolean changed = false;
        for (TeamCard team : teams.activeTeams(a.getTournamentId())) {
            if (!have.contains(team.id())) {
                budgets.save(new TeamBudget(a.getId(), team.id(), s.getInitialBudget()));
                changed = true;
            }
        }
        return changed;
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('AUCTION_CONFIGURE', 'AUCTION_CONTROL')")
    public void reorder(List<UUID> lotIds) {
        Auction a = lock();
        notCompleted(a);
        Map<UUID, AuctionLot> queued = lots.findByAuctionIdAndState(a.getId(), LotState.QUEUED).stream()
                .filter(l -> l.getRound() == a.getCurrentRound())
                .collect(Collectors.toMap(AuctionLot::getId, Function.identity()));
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(lotIds));
        if (!queued.keySet().containsAll(ids) || ids.size() != queued.size()) {
            throw ApiException.conflict("QUEUE_CHANGED", "The queue changed while you were reordering. Reload and try again.");
        }
        List<Integer> slots = queued.values().stream().map(AuctionLot::getQueueOrder).sorted().toList();
        for (int i = 0; i < ids.size(); i++) {
            queued.get(ids.get(i)).moveTo(slots.get(i));
        }
        events.publish(a, "QUEUE_CHANGED");
    }

    /** Per-player base price; null returns the player to the default (AUC-010). */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONFIGURE')")
    public void setBasePrice(UUID entryId, Long price) {
        Auction a = lock();
        notCompleted(a);
        AuctionSettings s = settingsService.current(a.getTournamentId());
        if (price != null && (price <= 0 || price > s.getInitialBudget())) {
            throw ApiException.unprocessable("INVALID_PRICE", "The base price must be above zero and within a team's budget.");
        }
        AuctionLot lot = lots.findByAuctionIdOrderByRoundAscQueueOrderAsc(a.getId()).stream()
                .filter(l -> l.getTournamentPlayerId().equals(entryId)).findFirst().orElse(null);
        if (lot != null && lot.getState() == LotState.OPEN) {
            throw ApiException.conflict("LOT_OPEN", "This player is on the block. Change the base price after the lot closes.");
        }
        Map<UUID, LotPlayer> found = players.lotPlayers(List.of(entryId));
        if (found.isEmpty()) {
            throw ApiException.notFound("Player");
        }
        Long before = found.get(entryId).basePriceOverride();
        players.setBasePrice(entryId, price);
        if (lot != null && (lot.getState() == LotState.QUEUED || lot.getState() == LotState.UNSOLD)) {
            lot.setBasePrice(price != null ? price : s.getDefaultBasePrice());
        }
        audit.record(AuditEvent.of("auction.base_price_set", "TournamentPlayer", entryId)
                .inTournament(a.getTournamentId())
                .withChange(mapOf("basePrice", before), mapOf("basePrice", price)));
        events.publish(a, "QUEUE_CHANGED");
    }

    // ---- Session -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public void start() {
        prepare();
        Auction a = lock();
        if (a.getStatus() != AuctionStatus.NOT_STARTED) {
            throw ApiException.conflict("ALREADY_STARTED", "The auction has already started.");
        }
        AuctionSettings s = settingsService.current(a.getTournamentId());
        List<TeamCard> active = teams.activeTeams(a.getTournamentId());
        if (active.size() < 2) {
            throw ApiException.unprocessable("NOT_READY", "At least two active teams are needed to start.");
        }
        if (lots.findByAuctionIdAndState(a.getId(), LotState.QUEUED).isEmpty()) {
            throw ApiException.unprocessable("NOT_READY", "There are no approved players in the queue.");
        }
        for (TeamCard team : active) {
            budgets.save(new TeamBudget(a.getId(), team.id(), s.getInitialBudget()));
        }
        a.start(AuctionSettingsService.snapshot(s), clock.instant());
        audit.record(AuctionEventLog.of("auction.started", a).withChange(null, AuctionSettingsService.snapshot(s)));
        events.publish(a, "STARTED");
    }

    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public void pause() {
        Auction a = lock();
        requireStatus(a, AuctionStatus.LIVE);
        a.setStatus(AuctionStatus.PAUSED);
        audit.record(AuctionEventLog.of("auction.paused", a));
        events.publish(a, "PAUSED");
    }

    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public void resume() {
        Auction a = lock();
        requireStatus(a, AuctionStatus.PAUSED);
        a.setStatus(AuctionStatus.LIVE);
        audit.record(AuctionEventLog.of("auction.resumed", a));
        events.publish(a, "RESUMED");
    }

    /** Ends the auction. Players never put up for auction are recorded as unsold. */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public void end(String reason) {
        Auction a = lock();
        if (a.getStatus() != AuctionStatus.LIVE && a.getStatus() != AuctionStatus.PAUSED) {
            throw ApiException.conflict("NOT_LIVE", "The auction isn't running.");
        }
        if (a.getCurrentLotId() != null) {
            throw ApiException.conflict("LOT_OPEN", "Close the current lot (sold, unsold or withdrawn) first.");
        }
        Instant now = clock.instant();
        List<AuctionLot> queued = lots.findByAuctionIdAndState(a.getId(), LotState.QUEUED);
        queued.forEach(l -> l.close(LotState.UNSOLD, now));
        a.end(now);
        audit.record(AuctionEventLog.of("auction.ended", a).withReason(blankToNull(reason)));
        events.publish(a, "ENDED");
    }

    // ---- Lots ----------------------------------------------------------------------------

    /** Puts the next queued player (or a chosen one) on the block (AUC-011). */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public UUID openLot(UUID lotId) {
        Auction a = lock();
        requireStatus(a, AuctionStatus.LIVE);
        if (a.getCurrentLotId() != null) {
            throw ApiException.conflict("LOT_OPEN", "Close the current lot before opening another.");
        }
        List<AuctionLot> queued = lots.findByAuctionIdAndState(a.getId(), LotState.QUEUED).stream()
                .filter(l -> l.getRound() == a.getCurrentRound())
                .sorted((x, y) -> Integer.compare(x.getQueueOrder(), y.getQueueOrder())).toList();
        AuctionLot lot = lotId == null ? queued.stream().findFirst()
                .orElseThrow(() -> ApiException.conflict("QUEUE_EMPTY", "No players are left in this round."))
                : queued.stream().filter(l -> l.getId().equals(lotId)).findFirst()
                        .orElseThrow(() -> ApiException.conflict("NOT_QUEUED", "That player isn't waiting in this round."));
        lot.open(a.nextLotNumber(), clock.instant());
        a.setCurrentLot(lot.getId());
        lots.flush();
        events.publish(a, "LOT_OPENED");
        return lot.getId();
    }

    /** Going once / going twice indicator for the display; any new bid resets it. */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public void call(UUID lotId, CallState state) {
        Auction a = lock();
        requireStatus(a, AuctionStatus.LIVE);
        AuctionLot lot = currentLot(a, lotId);
        if (state != CallState.NONE && lot.getLeadingTeamId() == null) {
            throw ApiException.conflict("NO_BIDS", "There's no bid to call yet.");
        }
        lot.call(state);
        events.publish(a, "CALL");
    }

    /** Records a bid. Safe to retry with the same idempotency key (AUC-013, AUC-030). */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public BidResult bid(BidCommand cmd) {
        if (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank() || cmd.idempotencyKey().length() > 80) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY", "Each bid needs an idempotency key.");
        }
        Auction a = lock();
        Optional<AuctionBid> earlier = bids.findByIdempotencyKey(cmd.idempotencyKey());
        if (earlier.isPresent()) {
            AuctionBid b = earlier.get();
            if (!b.getLotId().equals(cmd.lotId()) || !b.getTeamId().equals(cmd.teamId()) || b.getAmount() != cmd.amount()) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "This key was already used for a different bid.");
            }
            return new BidResult(b.getId(), b.getLotId(), b.getTeamId(), b.getAmount(), b.getBidNumber(),
                    a.getStateVersion(), true);
        }
        if (a.getStatus() == AuctionStatus.PAUSED) {
            throw ApiException.conflict("AUCTION_PAUSED", "The auction is paused.");
        }
        requireStatus(a, AuctionStatus.LIVE);
        AuctionLot lot = currentLot(a, cmd.lotId());
        AuctionSettings s = settingsService.current(a.getTournamentId());
        TeamBudget budget = budgets.findByAuctionIdAndTeamId(a.getId(), cmd.teamId())
                .orElseThrow(() -> ApiException.unprocessable("TEAM_NOT_IN_AUCTION", "That team isn't in the auction."));

        if (cmd.teamId().equals(lot.getLeadingTeamId())) {
            throw ApiException.conflict("ALREADY_LEADING", "This team already has the highest bid.");
        }
        if (budget.getSquadCount() >= s.getMaxSquadSize()) {
            throw ApiException.unprocessable("SQUAD_FULL", "This team's squad is full.");
        }
        long min = AuctionRules.nextMinimum(lot, s);
        if (cmd.amount() < min) {
            throw ApiException.conflict("BID_TOO_LOW", "The bid must be at least " + money(min) + ".");
        }
        if (cmd.amount() > budget.getRemaining()) {
            throw ApiException.unprocessable("EXCEEDS_BUDGET", "That's more than the team's remaining budget.");
        }
        if (cmd.amount() > AuctionRules.maxBid(budget, s)) {
            throw ApiException.unprocessable("RESERVE_REQUIRED",
                    "The team must keep enough to complete its minimum squad. Its maximum bid is "
                            + money(AuctionRules.maxBid(budget, s)) + ".");
        }
        AuctionBid b = bids.save(new AuctionBid(lot.getId(), lot.getRound(), cmd.teamId(), cmd.amount(),
                lot.nextBidNumber(), CurrentUser.get().map(CurrentUser.Actor::id).orElse(null), cmd.idempotencyKey(),
                clock.instant()));
        lot.lead(b.getId(), cmd.teamId(), cmd.amount());
        bids.flush();
        events.publish(a, "BID_ACCEPTED");
        return new BidResult(b.getId(), lot.getId(), cmd.teamId(), cmd.amount(), b.getBidNumber(), a.getStateVersion(),
                false);
    }

    /** Voids the latest bid; the previous accepted bid leads again (AUC-016). */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public void undoLastBid(UUID lotId) {
        Auction a = lock();
        requireStatus(a, AuctionStatus.LIVE, AuctionStatus.PAUSED);
        AuctionLot lot = currentLot(a, lotId);
        List<AuctionBid> accepted = bids.findByLotIdAndRoundAndStatusOrderByBidNumberDesc(lot.getId(), lot.getRound(),
                BidStatus.ACCEPTED);
        if (accepted.isEmpty()) {
            throw ApiException.conflict("NO_BIDS", "There's no bid to undo.");
        }
        AuctionBid last = accepted.get(0);
        last.voidBid(CurrentUser.get().map(CurrentUser.Actor::id).orElse(null), clock.instant());
        if (accepted.size() > 1) {
            AuctionBid prev = accepted.get(1);
            lot.lead(prev.getId(), prev.getTeamId(), prev.getAmount());
        } else {
            lot.clearLead();
            lot.call(CallState.NONE);
        }
        audit.record(AuctionEventLog.of("auction.bid_undone", a)
                .withChange(mapOf("teamId", last.getTeamId(), "amount", last.getAmount()), null));
        events.publish(a, "BID_UNDONE");
    }

    /**
     * Sells the lot to the leading team. {@code expectedBidId} must still be the leading bid, so a
     * late bid between "Sold?" and "Confirm" is never sold at the wrong price (AUC-017).
     */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public void sell(UUID lotId, UUID expectedBidId) {
        Auction a = lock();
        requireStatus(a, AuctionStatus.LIVE, AuctionStatus.PAUSED);
        AuctionLot lot = currentLot(a, lotId);
        if (lot.getLeadingTeamId() == null) {
            throw ApiException.conflict("NO_BIDS", "There's no bid to sell at.");
        }
        if (expectedBidId == null || !expectedBidId.equals(lot.getCurrentBidId())) {
            throw ApiException.conflict("LEAD_CHANGED", "The leading bid changed. Check the ladder and confirm again.");
        }
        AuctionSettings s = settingsService.current(a.getTournamentId());
        TeamBudget budget = budgets.findByAuctionIdAndTeamId(a.getId(), lot.getLeadingTeamId()).orElseThrow();
        if (budget.getSquadCount() >= s.getMaxSquadSize()) {
            throw ApiException.unprocessable("SQUAD_FULL", "The leading team's squad is already full.");
        }
        long price = lot.getCurrentAmount();
        Instant now = clock.instant();
        budget.buy(price);
        AuctionTransaction txn = transactions.save(new AuctionTransaction(a.getId(), lot.getId(), lot.getLeadingTeamId(),
                lot.getTournamentPlayerId(), price, CurrentUser.get().map(CurrentUser.Actor::id).orElse(null), now));
        teamPlayers.save(new TeamPlayer(a.getTournamentId(), lot.getLeadingTeamId(), lot.getTournamentPlayerId(),
                "AUCTION", price, txn.getId(), lot.getLotNumber()));
        lot.sold(lot.getLeadingTeamId(), price, now);
        a.setCurrentLot(null);
        teamPlayers.flush();
        audit.record(AuctionEventLog.of("auction.lot_sold", a)
                .withChange(null, mapOf("lot", lot.getLotNumber(), "entryId", lot.getTournamentPlayerId(), "teamId",
                        lot.getWinningTeamId(), "price", price)));
        events.publish(a, "LOT_SOLD");
    }

    /** No bids: the player goes to the unsold list for a later round (AUC-018). */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public void markUnsold(UUID lotId) {
        Auction a = lock();
        requireStatus(a, AuctionStatus.LIVE, AuctionStatus.PAUSED);
        AuctionLot lot = currentLot(a, lotId);
        if (lot.getLeadingTeamId() != null) {
            throw ApiException.conflict("HAS_BIDS", "This lot has a bid. Sell it, or undo the bids first.");
        }
        lot.close(LotState.UNSOLD, clock.instant());
        a.setCurrentLot(null);
        events.publish(a, "LOT_UNSOLD");
    }

    /** Takes a player out of the auction (open or waiting) with a reason (AUC-019). */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public void withdraw(UUID lotId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.unprocessable("REASON_REQUIRED", "Give a reason for withdrawing the player.");
        }
        Auction a = lock();
        notCompleted(a);
        AuctionLot lot = ownLot(a, lotId);
        if (lot.getState() != LotState.OPEN && lot.getState() != LotState.QUEUED && lot.getState() != LotState.UNSOLD) {
            throw ApiException.conflict("NOT_WITHDRAWABLE", "Only waiting, open or unsold players can be withdrawn.");
        }
        if (lot.getState() == LotState.OPEN) {
            a.setCurrentLot(null);
        }
        lot.clearLead();
        lot.close(LotState.WITHDRAWN, clock.instant());
        audit.record(AuctionEventLog.of("auction.lot_withdrawn", a).withReason(reason.trim())
                .withChange(null, mapOf("entryId", lot.getTournamentPlayerId())));
        events.publish(a, "LOT_WITHDRAWN");
    }

    /** Brings a withdrawn player back to the end of the current round's queue. */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public void restore(UUID lotId) {
        Auction a = lock();
        notCompleted(a);
        AuctionLot lot = ownLot(a, lotId);
        if (lot.getState() != LotState.WITHDRAWN) {
            throw ApiException.conflict("NOT_WITHDRAWN", "Only withdrawn players can be restored.");
        }
        Map<UUID, LotPlayer> p = players.lotPlayers(List.of(lot.getTournamentPlayerId()));
        if (p.isEmpty() || p.get(lot.getTournamentPlayerId()).status() != lk.ngbcl.player.domain.TournamentPlayerStatus.APPROVED) {
            throw ApiException.conflict("NOT_ELIGIBLE", "The player's registration isn't approved.");
        }
        lot.requeue(a.getCurrentRound(), lots.maxOrder(a.getId()) + 1);
        audit.record(AuctionEventLog.of("auction.lot_restored", a)
                .withChange(null, mapOf("entryId", lot.getTournamentPlayerId())));
        events.publish(a, "QUEUE_CHANGED");
    }

    /**
     * Starts the next round with unsold players: all of them, or only the ones selected from the
     * unsold panel (AUC-024). Only once the current round's queue is finished.
     */
    @Transactional
    @PreAuthorize("hasAuthority('AUCTION_CONTROL')")
    public int nextRound(List<UUID> selectedLotIds) {
        Auction a = lock();
        requireStatus(a, AuctionStatus.LIVE);
        AuctionSettings s = settingsService.current(a.getTournamentId());
        if (!s.isAllowReauction()) {
            throw ApiException.conflict("REAUCTION_DISABLED", "Re-auction is turned off in the auction settings.");
        }
        if (a.getCurrentLotId() != null) {
            throw ApiException.conflict("LOT_OPEN", "Close the current lot first.");
        }
        if (!lots.findByAuctionIdAndState(a.getId(), LotState.QUEUED).isEmpty()) {
            throw ApiException.conflict("ROUND_NOT_FINISHED", "Players are still waiting in this round.");
        }
        List<AuctionLot> unsold = lots.findByAuctionIdAndState(a.getId(), LotState.UNSOLD);
        List<AuctionLot> chosen;
        if (selectedLotIds == null || selectedLotIds.isEmpty()) {
            chosen = unsold.stream().sorted((x, y) -> Integer.compare(
                    x.getLotNumber() == null ? Integer.MAX_VALUE : x.getLotNumber(),
                    y.getLotNumber() == null ? Integer.MAX_VALUE : y.getLotNumber())).toList();
        } else {
            Map<UUID, AuctionLot> byId = unsold.stream().collect(Collectors.toMap(AuctionLot::getId, Function.identity()));
            Set<UUID> seen = new HashSet<>();
            chosen = new ArrayList<>();
            for (UUID id : selectedLotIds) {
                AuctionLot l = byId.get(id);
                if (l == null) {
                    throw ApiException.conflict("NOT_UNSOLD", "One of the selected players is no longer unsold. Reload and try again.");
                }
                if (seen.add(id)) {
                    chosen.add(l);
                }
            }
        }
        if (chosen.isEmpty()) {
            throw ApiException.conflict("NOTHING_UNSOLD", "There are no unsold players to re-auction.");
        }
        int round = a.getCurrentRound() + 1;
        int order = lots.maxOrder(a.getId());
        for (AuctionLot l : chosen) {
            l.requeue(round, ++order);
        }
        a.setCurrentRound(round);
        audit.record(AuctionEventLog.of("auction.round_started", a)
                .withChange(null, mapOf("round", round, "players", chosen.size())));
        events.publish(a, "ROUND_STARTED");
        return round;
    }

    // ---- Helpers ---------------------------------------------------------------------------

    /** Locks the edition's auction row for the rest of the transaction. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Auction lock() {
        UUID t = tournament.currentTournamentId();
        return auctions.lockByTournamentId(t)
                .orElseThrow(() -> ApiException.conflict("NOT_PREPARED", "Open the auction setup page first."));
    }

    private AuctionLot currentLot(Auction a, UUID lotId) {
        if (a.getCurrentLotId() == null) {
            throw ApiException.conflict("NO_OPEN_LOT", "No player is on the block.");
        }
        if (lotId != null && !lotId.equals(a.getCurrentLotId())) {
            throw ApiException.conflict("LOT_CHANGED", "A different player is on the block now.");
        }
        return lots.findById(a.getCurrentLotId()).orElseThrow();
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public AuctionLot ownLot(Auction a, UUID lotId) {
        AuctionLot lot = lots.findById(lotId).orElseThrow(() -> ApiException.notFound("Lot"));
        if (!lot.getAuctionId().equals(a.getId())) {
            throw ApiException.notFound("Lot");
        }
        return lot;
    }

    static void requireStatus(Auction a, AuctionStatus... allowed) {
        for (AuctionStatus s : allowed) {
            if (a.getStatus() == s) {
                return;
            }
        }
        String detail = switch (a.getStatus()) {
            case NOT_STARTED -> "The auction hasn't started.";
            case PAUSED -> "The auction is paused.";
            case COMPLETED -> "The auction has ended.";
            case LIVE -> "Not possible while the auction is live.";
        };
        throw ApiException.conflict(a.getStatus() == AuctionStatus.PAUSED ? "AUCTION_PAUSED" : "WRONG_STATE", detail);
    }

    static void notCompleted(Auction a) {
        if (a.getStatus() == AuctionStatus.COMPLETED) {
            throw ApiException.conflict("WRONG_STATE", "The auction has ended.");
        }
    }

    static String money(long amount) {
        return String.format(java.util.Locale.ROOT, "LKR %,d", amount);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
