package lk.ngbcl.scoring.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.competition.domain.MatchStatus;

/** Scoring views. Nulls are always written so clients can rely on every field being present. */
public final class ScoringDtos {

    private ScoringDtos() {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RulesView(int pointsToWin, int winBy, int pointCap, int gamesToWin, boolean custom) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record GameView(int number, int scoreA, int scoreB, String winnerSide) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TeamLite(String slug, String name, String shortCode, Short colorSlot, String logoUrl) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PlayerName(String slug, String fullName) {
    }

    /** Scorer's list ("My matches"). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ScorerCard(UUID matchId, UUID fixtureId, int fixtureNumber, String roundName, String groupName,
            int order, String categoryCode, String categoryName, TeamLite teamA, TeamLite teamB, List<PlayerName> sideA,
            List<PlayerName> sideB, Instant scheduledAt, String courtName, MatchStatus status, boolean lineupComplete,
            int gamesA, int gamesB, String returnedReason) {
    }

    /** Full state of one match for the scorer app and the results queue. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MatchState(UUID matchId, UUID fixtureId, int fixtureNumber, String roundName, String groupName,
            int order, String categoryCode, String categoryName, TeamLite teamA, TeamLite teamB, List<PlayerName> sideA,
            List<PlayerName> sideB, String courtName, Instant scheduledAt, MatchStatus status, RulesView rules,
            List<GameView> games, Integer currentGame, int scoreA, int scoreB, int gamesA, int gamesB, String server,
            String firstServer, String winnerSide, String outcome, String outcomeNote, long stateVersion, boolean canUndo,
            boolean lineupComplete, String returnedReason, boolean twoStepApproval, boolean allowWalkover,
            boolean allowRetirement, Instant submittedAt, String submittedBy, String scorerName) {
    }

    /** Public live-score card — public fields only. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicLiveMatch(UUID matchId, int fixtureNumber, String roundName, String groupName,
            String categoryCode, String categoryName, TeamLite teamA, TeamLite teamB, List<PlayerName> sideA,
            List<PlayerName> sideB, String courtName, Instant scheduledAt, MatchStatus status, boolean provisional,
            List<GameView> games, int scoreA, int scoreB, int gamesA, int gamesB, String server, String winnerSide,
            String outcome, long stateVersion) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicLive(List<PublicLiveMatch> live, List<PublicLiveMatch> recent, List<PublicLiveMatch> upNext) {
    }
}
