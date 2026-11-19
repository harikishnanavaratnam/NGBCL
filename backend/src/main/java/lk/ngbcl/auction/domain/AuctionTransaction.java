package lk.ngbcl.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;

/** A confirmed sale (AUC-029). Reversals keep the row and mark it REVERSED. */
@Entity
@Table(name = "auction_transactions")
public class AuctionTransaction extends UuidEntity {

    @Column(name = "auction_id", nullable = false, updatable = false)
    private UUID auctionId;

    @Column(name = "lot_id", nullable = false, updatable = false)
    private UUID lotId;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "tournament_player_id", nullable = false, updatable = false)
    private UUID tournamentPlayerId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SOLD";

    @Column(name = "confirmed_by", updatable = false)
    private UUID confirmedBy;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    @Column(name = "reversed_by")
    private UUID reversedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reverse_reason", length = 300)
    private String reverseReason;

    protected AuctionTransaction() {
    }

    public AuctionTransaction(UUID auctionId, UUID lotId, UUID teamId, UUID tournamentPlayerId, long amount,
            UUID confirmedBy, Instant at) {
        this.auctionId = auctionId;
        this.lotId = lotId;
        this.teamId = teamId;
        this.tournamentPlayerId = tournamentPlayerId;
        this.amount = amount;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = at;
    }

    public void reverse(UUID by, Instant at, String reason) {
        this.status = "REVERSED";
        this.reversedBy = by;
        this.reversedAt = at;
        this.reverseReason = reason;
    }

    public UUID getLotId() {
        return lotId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getTournamentPlayerId() {
        return tournamentPlayerId;
    }

    public long getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
