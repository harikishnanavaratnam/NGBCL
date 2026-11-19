package lk.ngbcl.competition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

/** One individual match inside a tie (FIX-006). Scores and results arrive in M6. */
@Entity
@Table(name = "matches")
public class Match extends AuditedEntity {

    @Column(name = "fixture_id", nullable = false, updatable = false)
    private UUID fixtureId;

    @Column(name = "match_order", nullable = false)
    private int matchOrder;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "players_per_side", nullable = false)
    private short playersPerSide;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "court_id")
    private UUID courtId;

    @Column(name = "scorer_user_id")
    private UUID scorerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private MatchStatus status = MatchStatus.SCHEDULED;

    protected Match() {
    }

    public Match(UUID fixtureId, int matchOrder, UUID categoryId, int playersPerSide) {
        this.fixtureId = fixtureId;
        this.matchOrder = matchOrder;
        this.categoryId = categoryId;
        this.playersPerSide = (short) playersPerSide;
    }

    public void schedule(Instant at, UUID courtId) {
        this.scheduledAt = at;
        this.courtId = courtId;
    }

    public void assignScorer(UUID userId) {
        this.scorerUserId = userId;
    }

    public UUID getFixtureId() {
        return fixtureId;
    }

    public int getMatchOrder() {
        return matchOrder;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public int getPlayersPerSide() {
        return playersPerSide;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public UUID getCourtId() {
        return courtId;
    }

    public UUID getScorerUserId() {
        return scorerUserId;
    }

    public MatchStatus getStatus() {
        return status;
    }
}
