package lk.ngbcl.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Edition scoring defaults (Settings › Scoring rules) and the result-approval mode. */
@Entity
@Table(name = "scoring_settings")
public class ScoringSettings {

    @Id
    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "points_to_win", nullable = false)
    private int pointsToWin = 21;

    @Column(name = "win_by", nullable = false)
    private int winBy = 2;

    @Column(name = "point_cap", nullable = false)
    private int pointCap = 30;

    @Column(name = "games_to_win", nullable = false)
    private int gamesToWin = 2;

    @Column(name = "allow_walkover", nullable = false)
    private boolean allowWalkover = true;

    @Column(name = "allow_retirement", nullable = false)
    private boolean allowRetirement = true;

    @Column(name = "two_step_approval", nullable = false)
    private boolean twoStepApproval = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ScoringSettings() {
    }

    public ScoringSettings(UUID tournamentId) {
        this.tournamentId = tournamentId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void update(int pointsToWin, int winBy, int pointCap, int gamesToWin, boolean allowWalkover,
            boolean allowRetirement, boolean twoStepApproval) {
        this.pointsToWin = pointsToWin;
        this.winBy = winBy;
        this.pointCap = pointCap;
        this.gamesToWin = gamesToWin;
        this.allowWalkover = allowWalkover;
        this.allowRetirement = allowRetirement;
        this.twoStepApproval = twoStepApproval;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public int getPointsToWin() {
        return pointsToWin;
    }

    public int getWinBy() {
        return winBy;
    }

    public int getPointCap() {
        return pointCap;
    }

    public int getGamesToWin() {
        return gamesToWin;
    }

    public boolean isAllowWalkover() {
        return allowWalkover;
    }

    public boolean isAllowRetirement() {
        return allowRetirement;
    }

    public boolean isTwoStepApproval() {
        return twoStepApproval;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
