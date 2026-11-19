package lk.ngbcl.competition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;

/** One game of a match: running score while in play, final once it has a winner. */
@Entity
@Table(name = "match_games")
public class MatchGame extends UuidEntity {

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "game_number", nullable = false)
    private int gameNumber;

    @Column(name = "score_a", nullable = false)
    private int scoreA;

    @Column(name = "score_b", nullable = false)
    private int scoreB;

    @Column(name = "winner_side", length = 1)
    private String winnerSide;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected MatchGame() {
    }

    public MatchGame(UUID matchId, int gameNumber, int scoreA, int scoreB, String winnerSide, Instant completedAt) {
        this.matchId = matchId;
        this.gameNumber = gameNumber;
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.winnerSide = winnerSide;
        this.completedAt = completedAt;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public int getGameNumber() {
        return gameNumber;
    }

    public int getScoreA() {
        return scoreA;
    }

    public int getScoreB() {
        return scoreB;
    }

    public String getWinnerSide() {
        return winnerSide;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
