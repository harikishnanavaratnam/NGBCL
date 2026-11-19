package lk.ngbcl.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

/** Roster entry (ROS-001). The partial unique index allows one ACTIVE team per player per edition. */
@Entity
@Table(name = "team_players")
public class TeamPlayer extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "tournament_player_id", nullable = false)
    private UUID tournamentPlayerId;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "purchase_price")
    private Long purchasePrice;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "lot_number")
    private Integer lotNumber;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_reason", length = 300)
    private String releasedReason;

    protected TeamPlayer() {
    }

    public TeamPlayer(UUID tournamentId, UUID teamId, UUID tournamentPlayerId, String source, Long purchasePrice,
            UUID transactionId, Integer lotNumber) {
        this.tournamentId = tournamentId;
        this.teamId = teamId;
        this.tournamentPlayerId = tournamentPlayerId;
        this.source = source;
        this.purchasePrice = purchasePrice;
        this.transactionId = transactionId;
        this.lotNumber = lotNumber;
    }

    public void release(Instant at, String reason) {
        this.active = false;
        this.releasedAt = at;
        this.releasedReason = reason;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getTournamentPlayerId() {
        return tournamentPlayerId;
    }

    public String getSource() {
        return source;
    }

    public Long getPurchasePrice() {
        return purchasePrice;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public Integer getLotNumber() {
        return lotNumber;
    }

    public boolean isActive() {
        return active;
    }
}
