package lk.ngbcl.competition.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.competition.domain.CompetitionFormat;
import lk.ngbcl.competition.domain.FixtureStatus;
import lk.ngbcl.competition.domain.GenderRule;
import lk.ngbcl.competition.domain.MatchStatus;
import lk.ngbcl.competition.domain.StageType;

/**
 * Competition views. Nulls are always written (the app default omits them) so clients can rely on
 * every field being present, e.g. {@code teamA: null} for an undecided knockout slot.
 */
public final class CompetitionDtos {

    private CompetitionDtos() {
    }

    // ---- Setup ---------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SettingsView(CompetitionFormat format, int groupCount, int qualifiersPerGroup, boolean thirdPlaceMatch,
            int maxMatchesPerPlayer, int matchMinutes, int restMinutes, Long version) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CategoryView(UUID id, String code, String name, int playersPerSide, GenderRule genderRule,
            boolean active, boolean inUse, Long version) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TemplateItemView(int position, UUID categoryId, String code, String name) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CourtView(UUID id, String name, boolean active, Long version) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SetupView(SettingsView settings, List<CategoryView> categories, List<TemplateItemView> template,
            List<CourtView> courts, boolean fixturesGenerated, boolean fixturesPublished) {
    }

    // ---- Groups --------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TeamRef(UUID id, String slug, String name, String shortCode, Short colorSlot, String logoUrl) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record GroupView(UUID id, String name, List<TeamRef> teams) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record GroupsView(List<GroupView> groups, List<TeamRef> unassigned, int groupCount, boolean locked) {
    }

    // ---- Fixtures ------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PlayerRef(UUID entryId, String slug, String fullName, String gender) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StaffRef(UUID id, String displayName) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MatchView(UUID id, int order, UUID categoryId, String categoryCode, String categoryName,
            int playersPerSide, GenderRule genderRule, Instant scheduledAt, List<PlayerRef> sideA, List<PlayerRef> sideB,
            StaffRef scorer, MatchStatus status, boolean lineupComplete, Integer pointsToWin, Integer winBy,
            Integer pointCap, Integer gamesToWin, boolean rulesCustom, int gamesA, int gamesB, String winnerSide,
            String outcome, List<String> gameScores) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FixtureView(UUID id, int number, UUID stageId, String stageName, StageType stageType, UUID groupId,
            String groupName, int roundNumber, String roundName, Integer bracketPosition, TeamRef teamA, TeamRef teamB,
            String sourceA, String sourceB, Instant scheduledAt, Instant endsAt, UUID courtId, String courtName,
            FixtureStatus status, boolean published, boolean changedSincePublish, UUID winnerToFixtureId,
            String winnerToSide, UUID loserToFixtureId, String loserToSide, List<MatchView> matches,
            boolean lineupsComplete, List<String> warnings, Long version, int matchesWonA, int matchesWonB,
            String result) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StageView(UUID id, String name, StageType type, int order) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FixturesView(List<StageView> stages, List<GroupView> groups, List<FixtureView> fixtures,
            List<CourtView> courts, List<StaffRef> scorers, int matchMinutes, int restMinutes, int unpublished,
            int changed) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RosterView(TeamRef team, List<PlayerRef> players) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublishItem(UUID fixtureId, int number, String label, String change) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublishPreview(List<PublishItem> items, int unscheduled) {
    }

    // ---- Public --------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicTeamRef(String slug, String name, String shortCode, Short colorSlot, String logoUrl) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicPlayerRef(String slug, String fullName) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicMatch(int order, String categoryCode, String categoryName, List<PublicPlayerRef> sideA,
            List<PublicPlayerRef> sideB, Instant scheduledAt, MatchStatus status, List<String> games, String winnerSide,
            String outcome, boolean provisional) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicFixture(int number, String stageName, StageType stageType, String groupName, int roundNumber,
            String roundName, Integer bracketPosition, PublicTeamRef teamA, PublicTeamRef teamB, String sourceA,
            String sourceB, Instant scheduledAt, String courtName, FixtureStatus status, Integer winnerToNumber,
            List<PublicMatch> matches, int matchesWonA, int matchesWonB, String result) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicGroup(String name, List<PublicTeamRef> teams) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicFixtures(CompetitionFormat format, List<PublicGroup> groups, List<PublicFixture> fixtures) {
    }
}
