package lk.ngbcl.competition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A tie between two teams, made of several matches (FIX-003..005). Knockout fixtures start with
 * empty slots and a label saying where each team comes from ("Group A #1", "Winner of SF1").
 */
@Entity
@Table(name = "team_fixtures")
public class TeamFixture extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "stage_id", nullable = false)
    private UUID stageId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "fixture_number", nullable = false)
    private int fixtureNumber;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "round_name", nullable = false, length = 40)
    private String roundName;

    @Column(name = "bracket_position")
    private Integer bracketPosition;

    @Column(name = "team_a_id")
    private UUID teamAId;

    @Column(name = "team_b_id")
    private UUID teamBId;

    @Column(name = "source_a", length = 60)
    private String sourceA;

    @Column(name = "source_b", length = 60)
    private String sourceB;

    @Column(name = "winner_to_fixture_id")
    private UUID winnerToFixtureId;

    @Column(name = "winner_to_side", length = 1)
    private String winnerToSide;

    @Column(name = "loser_to_fixture_id")
    private UUID loserToFixtureId;

    @Column(name = "loser_to_side", length = 1)
    private String loserToSide;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "court_id")
    private UUID courtId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private FixtureStatus status = FixtureStatus.DRAFT;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "changed_since_publish", nullable = false)
    private boolean changedSincePublish;

    /** What the public sees: frozen at publish time so later edits stay private until republished. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "published_snapshot", columnDefinition = "jsonb")
    private String publishedSnapshot;

    @Column(name = "matches_won_a", nullable = false)
    private int matchesWonA;

    @Column(name = "matches_won_b", nullable = false)
    private int matchesWonB;

    @Column(name = "result", length = 5)
    private String result;

    @Column(name = "winner_team_id")
    private UUID winnerTeamId;

    protected TeamFixture() {
    }

    public TeamFixture(UUID tournamentId, UUID stageId, UUID groupId, int fixtureNumber, int roundNumber,
            String roundName, Integer bracketPosition) {
        this.tournamentId = tournamentId;
        this.stageId = stageId;
        this.groupId = groupId;
        this.fixtureNumber = fixtureNumber;
        this.roundNumber = roundNumber;
        this.roundName = roundName;
        this.bracketPosition = bracketPosition;
    }

    public void setTeams(UUID a, UUID b) {
        this.teamAId = a;
        this.teamBId = b;
        markChanged();
    }

    public void setTeam(String side, UUID team) {
        if ("A".equals(side)) {
            this.teamAId = team;
        } else {
            this.teamBId = team;
        }
        markChanged();
    }

    public void setSources(String a, String b) {
        this.sourceA = a;
        this.sourceB = b;
    }

    public void winnerTo(UUID fixtureId, String side) {
        this.winnerToFixtureId = fixtureId;
        this.winnerToSide = side;
    }

    public void loserTo(UUID fixtureId, String side) {
        this.loserToFixtureId = fixtureId;
        this.loserToSide = side;
    }

    public void schedule(Instant at, UUID courtId) {
        this.scheduledAt = at;
        this.courtId = courtId;
        markChanged();
    }

    /** Stores the public copy; call after {@link #publish} so the copy shows the published status. */
    public void freeze(String snapshot) {
        this.publishedSnapshot = snapshot;
    }

    public void publish(Instant at) {
        this.published = true;
        this.publishedAt = at;
        this.changedSincePublish = false;
        if (status == FixtureStatus.DRAFT) {
            status = FixtureStatus.SCHEDULED;
        }
    }

    /** Anything the public sees changed after publishing — shows up in the publish diff. */
    public void markChanged() {
        if (published) {
            changedSincePublish = true;
        }
    }

    public boolean isLocked() {
        return status == FixtureStatus.LIVE || status == FixtureStatus.COMPLETED;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public UUID getStageId() {
        return stageId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public int getFixtureNumber() {
        return fixtureNumber;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public String getRoundName() {
        return roundName;
    }

    public Integer getBracketPosition() {
        return bracketPosition;
    }

    public UUID getTeamAId() {
        return teamAId;
    }

    public UUID getTeamBId() {
        return teamBId;
    }

    public String getSourceA() {
        return sourceA;
    }

    public String getSourceB() {
        return sourceB;
    }

    public UUID getWinnerToFixtureId() {
        return winnerToFixtureId;
    }

    public String getWinnerToSide() {
        return winnerToSide;
    }

    public UUID getLoserToFixtureId() {
        return loserToFixtureId;
    }

    public String getLoserToSide() {
        return loserToSide;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public UUID getCourtId() {
        return courtId;
    }

    public FixtureStatus getStatus() {
        return status;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public boolean isChangedSincePublish() {
        return changedSincePublish;
    }

    public String getPublishedSnapshot() {
        return publishedSnapshot;
    }

    public UUID getWinnerTeamId() {
        return winnerTeamId;
    }

    /** Live tally; {@code result} is set (A, B or DRAW) only when every match is final. */
    public void setScore(int wonA, int wonB, String result, UUID winnerTeamId, FixtureStatus status) {
        this.matchesWonA = wonA;
        this.matchesWonB = wonB;
        this.result = result;
        this.winnerTeamId = winnerTeamId;
        this.status = status;
    }

    public int getMatchesWonA() {
        return matchesWonA;
    }

    public int getMatchesWonB() {
        return matchesWonB;
    }

    public String getResult() {
        return result;
    }
}
