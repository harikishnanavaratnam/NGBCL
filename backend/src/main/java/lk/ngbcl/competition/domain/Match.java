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

    @Column(name = "points_to_win")
    private Integer pointsToWin;

    @Column(name = "win_by")
    private Integer winBy;

    @Column(name = "point_cap")
    private Integer pointCap;

    @Column(name = "games_to_win")
    private Integer gamesToWin;

    @Column(name = "rules_custom", nullable = false)
    private boolean rulesCustom;

    @Column(name = "first_server", length = 1)
    private String firstServer;

    @Column(name = "outcome", length = 10)
    private String outcome;

    @Column(name = "winner_side", length = 1)
    private String winnerSide;

    @Column(name = "games_a", nullable = false)
    private int gamesA;

    @Column(name = "games_b", nullable = false)
    private int gamesB;

    @Column(name = "points_a", nullable = false)
    private int pointsA;

    @Column(name = "points_b", nullable = false)
    private int pointsB;

    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "started_by")
    private UUID startedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "returned_reason", length = 300)
    private String returnedReason;

    @Column(name = "outcome_note", length = 300)
    private String outcomeNote;

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

    // ---- Scoring (M6) ----------------------------------------------------------------------

    /** Rules set for this match before it starts (null = edition defaults at start). */
    public void setRules(int pointsToWin, int winBy, int cap, int gamesToWin, boolean custom) {
        this.pointsToWin = pointsToWin;
        this.winBy = winBy;
        this.pointCap = cap;
        this.gamesToWin = gamesToWin;
        this.rulesCustom = custom;
    }

    public void clearRules() {
        this.pointsToWin = null;
        this.winBy = null;
        this.pointCap = null;
        this.gamesToWin = null;
        this.rulesCustom = false;
    }

    public void start(String firstServer, UUID by, Instant at) {
        this.status = MatchStatus.LIVE;
        this.firstServer = firstServer;
        this.startedBy = by;
        this.startedAt = at;
    }

    public long bump() {
        return ++stateVersion;
    }

    /** Totals after the games changed; winner null while the match is undecided. */
    public void tally(int gamesA, int gamesB, int pointsA, int pointsB, String winnerSide, String outcome) {
        this.gamesA = gamesA;
        this.gamesB = gamesB;
        this.pointsA = pointsA;
        this.pointsB = pointsB;
        this.winnerSide = winnerSide;
        this.outcome = outcome;
    }

    public void setOutcomeNote(String note) {
        this.outcomeNote = note;
    }

    public void submit(UUID by, Instant at) {
        this.status = MatchStatus.SUBMITTED;
        this.submittedBy = by;
        this.submittedAt = at;
        this.returnedReason = null;
    }

    public void confirm(UUID by, Instant at) {
        this.status = MatchStatus.COMPLETED;
        this.confirmedBy = by;
        this.confirmedAt = at;
    }

    public void returnToScorer(String reason) {
        this.status = MatchStatus.LIVE;
        this.returnedReason = reason;
        this.submittedAt = null;
        this.submittedBy = null;
    }

    public Integer getPointsToWin() {
        return pointsToWin;
    }

    public Integer getWinBy() {
        return winBy;
    }

    public Integer getPointCap() {
        return pointCap;
    }

    public Integer getGamesToWin() {
        return gamesToWin;
    }

    public boolean isRulesCustom() {
        return rulesCustom;
    }

    public String getFirstServer() {
        return firstServer;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getWinnerSide() {
        return winnerSide;
    }

    public int getGamesA() {
        return gamesA;
    }

    public int getGamesB() {
        return gamesB;
    }

    public int getPointsA() {
        return pointsA;
    }

    public int getPointsB() {
        return pointsB;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getReturnedReason() {
        return returnedReason;
    }

    public String getOutcomeNote() {
        return outcomeNote;
    }
}
