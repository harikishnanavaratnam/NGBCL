package lk.ngbcl.standings.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/** Standings, knockout seeding and statistics views. Nulls are always written. */
public final class StandingsDtos {

    private StandingsDtos() {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TeamRef(String slug, String name, String shortCode, Short colorSlot, String logoUrl) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RulesView(int pointsWin, int pointsDraw, int pointsLoss, List<String> tiebreakers, Long version) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StandingRow(int position, TeamRef team, int played, int won, int drawn, int lost, int matchesWon,
            int matchesLost, int gamesWon, int gamesLost, int pointsWon, int pointsLost, int points, List<String> form,
            boolean tied, boolean qualifies) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record GroupTable(String name, boolean complete, int tiesPlayed, int tiesTotal, int qualifiers,
            List<StandingRow> rows) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StandingsView(String format, RulesView rules, List<GroupTable> groups) {
    }

    /** One knockout slot that is filled from a final group position ("Group A #1"). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SeedingSlot(UUID fixtureId, int fixtureNumber, String roundName, String side, String source,
            TeamRef currentTeam, TeamRef proposedTeam, boolean ready, boolean locked, boolean changes, String note) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SeedingView(boolean allGroupsComplete, int pendingChanges, List<SeedingSlot> slots) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SeedingResult(int tiesUpdated, SeedingView view) {
    }

    // ---- Statistics ------------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CategoryRecord(String code, String name, int played, int won) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PlayerStat(String slug, String fullName, TeamRef team, int played, int won, int lost, int gamesWon,
            int gamesLost, int pointsWon, int pointsLost, int walkoversWon, List<CategoryRecord> categories) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TeamStat(TeamRef team, int tiesPlayed, int tiesWon, int tiesDrawn, int tiesLost, int matchesWon,
            int matchesLost, int gamesWon, int gamesLost, int pointsWon, int pointsLost, List<String> form) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Leader(String slug, String fullName, TeamRef team, int played, int won, int gameDiff) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CategoryLeaders(String code, String name, List<Leader> leaders) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StatsView(List<PlayerStat> players, List<TeamStat> teams, List<CategoryLeaders> leaders) {
    }
}
