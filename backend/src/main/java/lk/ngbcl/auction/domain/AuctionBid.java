package lk.ngbcl.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;

/** Append-only bid record (AUC-034). Undo marks a bid VOIDED; rows are never deleted. */
@Entity
@Table(name = "auction_bids")
public class AuctionBid extends UuidEntity {

    @Column(name = "lot_id", nullable = false, updatable = false)
    private UUID lotId;

    @Column(name = "round", nullable = false, updatable = false)
    private int round;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "bid_number", nullable = false, updatable = false)
    private int bidNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BidStatus status = BidStatus.ACCEPTED;

    @Column(name = "placed_by", updatable = false)
    private UUID placedBy;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 80)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private UUID voidedBy;

    protected AuctionBid() {
    }

    public AuctionBid(UUID lotId, int round, UUID teamId, long amount, int bidNumber, UUID placedBy,
            String idempotencyKey, Instant at) {
        this.lotId = lotId;
        this.round = round;
        this.teamId = teamId;
        this.amount = amount;
        this.bidNumber = bidNumber;
        this.placedBy = placedBy;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = at;
    }

    public void voidBid(UUID by, Instant at) {
        this.status = BidStatus.VOIDED;
        this.voidedBy = by;
        this.voidedAt = at;
    }

    public UUID getLotId() {
        return lotId;
    }

    public int getRound() {
        return round;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public long getAmount() {
        return amount;
    }

    public int getBidNumber() {
        return bidNumber;
    }

    public BidStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
