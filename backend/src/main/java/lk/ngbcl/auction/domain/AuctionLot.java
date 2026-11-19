package lk.ngbcl.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

/** One player in the auction. Re-auction re-queues the same lot in a later round. */
@Entity
@Table(name = "auction_lots")
public class AuctionLot extends AuditedEntity {

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "tournament_player_id", nullable = false)
    private UUID tournamentPlayerId;

    @Column(name = "base_price", nullable = false)
    private long basePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private LotState state = LotState.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_state", nullable = false, length = 20)
    private CallState callState = CallState.NONE;

    @Column(name = "round", nullable = false)
    private int round = 1;

    @Column(name = "queue_order", nullable = false)
    private int queueOrder;

    @Column(name = "lot_number")
    private Integer lotNumber;

    @Column(name = "current_bid_id")
    private UUID currentBidId;

    @Column(name = "current_amount")
    private Long currentAmount;

    @Column(name = "leading_team_id")
    private UUID leadingTeamId;

    @Column(name = "bid_count", nullable = false)
    private int bidCount;

    @Column(name = "final_price")
    private Long finalPrice;

    @Column(name = "winning_team_id")
    private UUID winningTeamId;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected AuctionLot() {
    }

    public AuctionLot(UUID auctionId, UUID tournamentPlayerId, long basePrice, int round, int queueOrder) {
        this.auctionId = auctionId;
        this.tournamentPlayerId = tournamentPlayerId;
        this.basePrice = basePrice;
        this.round = round;
        this.queueOrder = queueOrder;
    }

    public void open(int lotNumber, Instant at) {
        this.state = LotState.OPEN;
        this.callState = CallState.NONE;
        this.lotNumber = lotNumber;
        this.openedAt = at;
        this.closedAt = null;
        clearLead();
    }

    public void lead(UUID bidId, UUID teamId, long amount) {
        this.currentBidId = bidId;
        this.leadingTeamId = teamId;
        this.currentAmount = amount;
        this.callState = CallState.NONE;
    }

    public void clearLead() {
        this.currentBidId = null;
        this.leadingTeamId = null;
        this.currentAmount = null;
    }

    public int nextBidNumber() {
        return ++bidCount;
    }

    public void call(CallState call) {
        this.callState = call;
    }

    public void sold(UUID teamId, long price, Instant at) {
        this.state = LotState.SOLD;
        this.winningTeamId = teamId;
        this.finalPrice = price;
        this.closedAt = at;
        this.callState = CallState.NONE;
    }

    public void close(LotState outcome, Instant at) {
        this.state = outcome;
        this.closedAt = at;
        this.callState = CallState.NONE;
    }

    /** Back into the queue for a later round (re-auction) or after an allocation is released. */
    public void requeue(int round, int queueOrder) {
        this.state = LotState.QUEUED;
        this.round = round;
        this.queueOrder = queueOrder;
        this.callState = CallState.NONE;
        this.winningTeamId = null;
        this.finalPrice = null;
        this.closedAt = null;
        clearLead();
    }

    /** Correction: the sale was reversed; the player is unsold until re-auctioned. */
    public void reverseSale(Instant at) {
        this.state = LotState.UNSOLD;
        this.winningTeamId = null;
        this.finalPrice = null;
        this.closedAt = at;
        clearLead();
    }

    /** Correction: the sale moved to another team. */
    public void reassign(UUID teamId, long price) {
        this.winningTeamId = teamId;
        this.finalPrice = price;
    }

    public void setBasePrice(long basePrice) {
        this.basePrice = basePrice;
    }

    public void moveTo(int order) {
        this.queueOrder = order;
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getTournamentPlayerId() {
        return tournamentPlayerId;
    }

    public long getBasePrice() {
        return basePrice;
    }

    public LotState getState() {
        return state;
    }

    public CallState getCallState() {
        return callState;
    }

    public int getRound() {
        return round;
    }

    public int getQueueOrder() {
        return queueOrder;
    }

    public Integer getLotNumber() {
        return lotNumber;
    }

    public UUID getCurrentBidId() {
        return currentBidId;
    }

    public Long getCurrentAmount() {
        return currentAmount;
    }

    public UUID getLeadingTeamId() {
        return leadingTeamId;
    }

    public int getBidCount() {
        return bidCount;
    }

    public Long getFinalPrice() {
        return finalPrice;
    }

    public UUID getWinningTeamId() {
        return winningTeamId;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
