package lk.ngbcl.standings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Edition standings rules (Settings › Standings): table points and the tie-breaker order. */
@Entity
@Table(name = "standings_settings")
public class StandingsSettings {

    public static final String DEFAULT_TIEBREAKERS = "MATCHES_WON,GAME_DIFF,POINT_DIFF,HEAD_TO_HEAD";

    @Id
    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "points_win", nullable = false)
    private int pointsWin = 2;

    @Column(name = "points_draw", nullable = false)
    private int pointsDraw = 1;

    @Column(name = "points_loss", nullable = false)
    private int pointsLoss = 0;

    @Column(name = "tiebreakers", nullable = false, length = 200)
    private String tiebreakers = DEFAULT_TIEBREAKERS;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected StandingsSettings() {
    }

    public StandingsSettings(UUID tournamentId) {
        this.tournamentId = tournamentId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void update(int win, int draw, int loss, String tiebreakers) {
        this.pointsWin = win;
        this.pointsDraw = draw;
        this.pointsLoss = loss;
        this.tiebreakers = tiebreakers;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public int getPointsWin() {
        return pointsWin;
    }

    public int getPointsDraw() {
        return pointsDraw;
    }

    public int getPointsLoss() {
        return pointsLoss;
    }

    public String getTiebreakers() {
        return tiebreakers;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
