package lk.ngbcl.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The live auction for an edition. Every command locks this row (SELECT … FOR UPDATE), so
 * commands are serialised and {@code stateVersion} increases by exactly one per change —
 * clients use it to detect missed realtime messages (SRS §8.3).
 */
@Entity
@Table(name = "auctions")
public class Auction extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuctionStatus status = AuctionStatus.NOT_STARTED;

    @Column(name = "current_round", nullable = false)
    private int currentRound = 1;

    @Column(name = "current_lot_id")
    private UUID currentLotId;

    @Column(name = "lots_opened", nullable = false)
    private int lotsOpened;

    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> settingsSnapshot;

    @Column(name = "last_event", length = 40)
    private String lastEvent;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "last_action_at")
    private Instant lastActionAt;

    protected Auction() {
    }

    public Auction(UUID tournamentId) {
        this.tournamentId = tournamentId;
    }

    /** Records that something changed; returns the new state version. */
    public long bump(String event, Instant at) {
        this.lastEvent = event;
        this.lastActionAt = at;
        return ++stateVersion;
    }

    public void start(Map<String, Object> snapshot, Instant at) {
        this.status = AuctionStatus.LIVE;
        this.settingsSnapshot = snapshot;
        this.startedAt = at;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public void end(Instant at) {
        this.status = AuctionStatus.COMPLETED;
        this.endedAt = at;
        this.currentLotId = null;
    }

    public int nextLotNumber() {
        return ++lotsOpened;
    }

    public void setCurrentLot(UUID lotId) {
        this.currentLotId = lotId;
    }

    public void setCurrentRound(int round) {
        this.currentRound = round;
    }

    public void updateSnapshot(Map<String, Object> snapshot) {
        this.settingsSnapshot = snapshot;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public UUID getCurrentLotId() {
        return currentLotId;
    }

    public int getLotsOpened() {
        return lotsOpened;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Map<String, Object> getSettingsSnapshot() {
        return settingsSnapshot;
    }

    public String getLastEvent() {
        return lastEvent;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Instant getLastActionAt() {
        return lastActionAt;
    }
}
