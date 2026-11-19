package lk.ngbcl.auction.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.auction.domain.AuctionStatus;
import lk.ngbcl.auction.domain.BidStatus;
import lk.ngbcl.auction.domain.CallState;
import lk.ngbcl.auction.domain.LotState;

/**
 * Views of the auction. Nulls are always written (the app default omits them) so clients can
 * rely on every field being present, e.g. {@code currentLot: null} between lots. Staff views carry
 * ids for commands; Public* views carry public fields only.
 */
public final class AuctionDtos {

    private AuctionDtos() {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SettingsView(long initialBudget, int maxSquadSize, Integer minSquadSize, long defaultBasePrice,
            long minIncrement, boolean allowReauction, boolean allowManualAdjustment, Instant startsAt,
            boolean locked, Long version) {
    }

    // ---- Console (staff) -------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LotPlayerView(UUID entryId, String slug, String fullName, String categoryName, String photoUrl) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BidView(UUID id, UUID teamId, String teamName, String teamShortCode, Short teamColorSlot, long amount,
            int bidNumber, BidStatus status, Instant createdAt) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LotView(UUID id, Integer lotNumber, int round, LotState state, CallState callState, long basePrice,
            boolean basePriceOverridden, Long currentAmount, UUID leadingTeamId, long nextMinimum, Long finalPrice,
            UUID winningTeamId, LotPlayerView player, List<BidView> bids, Instant openedAt) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record QueueItem(UUID lotId, UUID entryId, String fullName, String categoryName, String photoUrl,
            long basePrice, boolean basePriceOverridden, LotState state, int round, int queueOrder, Integer lotNumber,
            Long finalPrice, UUID winningTeamId, String winningTeamName) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TeamRail(UUID teamId, String slug, String name, String shortCode, Short colorSlot, String logoUrl,
            long initialBudget, long adjustments, long spent, long remaining, int squadCount, int maxSquadSize,
            long maxBid, boolean canBid, String blockedReason) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Counts(int total, int queued, int sold, int unsold, int withdrawn) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ConsoleSnapshot(UUID auctionId, AuctionStatus status, int round, long stateVersion, int lotsOpened,
            Instant startedAt, Instant lastActionAt, String lastEvent, SettingsView settings, LotView currentLot,
            List<QueueItem> queue, List<TeamRail> teams, Counts counts, boolean canStartNextRound,
            List<String> startBlockers) {
    }

    // ---- Public ---------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicTeamTicker(String slug, String name, String shortCode, Short colorSlot, String logoUrl,
            int squadCount, int maxSquadSize, Long remaining, Long spent) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicBid(String teamName, String teamShortCode, Short teamColorSlot, long amount) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicLot(Integer lotNumber, int round, LotState state, CallState callState, String slug,
            String fullName, String categoryName, String photoUrl, long basePrice, Long currentAmount,
            PublicTeamTicker leadingTeam, List<PublicBid> lastBids, Long finalPrice, PublicTeamTicker winningTeam) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicBuy(Integer lotNumber, String slug, String fullName, String categoryName, String photoUrl,
            String teamName, String teamSlug, Short teamColorSlot, Long price) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicAuctionState(AuctionStatus status, int round, long stateVersion, int lotsOpened, int totalLots,
            Instant startsAt, PublicLot currentLot, PublicLot lastClosed, List<PublicTeamTicker> teams,
            List<PublicBuy> topBuys, boolean budgetsPublic) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicResults(AuctionStatus status, List<PublicBuy> sold, List<PublicBuy> unsold,
            List<PublicTeamTicker> teams, boolean budgetsPublic, boolean pricesPublic) {
    }

    // ---- Rosters (staff) ------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RosterEntry(UUID lotId, UUID entryId, String slug, String fullName, String categoryName, String photoUrl,
            String source, Long purchasePrice, Integer lotNumber) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TeamRoster(TeamRail team, List<RosterEntry> players) {
    }
}
