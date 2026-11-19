package lk.ngbcl.competition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;

/** A player in a match line-up: side A or B, position 1 (or 2 in doubles). */
@Entity
@Table(name = "match_players")
public class MatchPlayer extends UuidEntity {

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "side", nullable = false, length = 1)
    private String side;

    @Column(name = "position", nullable = false)
    private short position;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "tournament_player_id", nullable = false)
    private UUID tournamentPlayerId;

    protected MatchPlayer() {
    }

    public MatchPlayer(UUID matchId, String side, int position, UUID teamId, UUID tournamentPlayerId) {
        this.matchId = matchId;
        this.side = side;
        this.position = (short) position;
        this.teamId = teamId;
        this.tournamentPlayerId = tournamentPlayerId;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public String getSide() {
        return side;
    }

    public int getPosition() {
        return position;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getTournamentPlayerId() {
        return tournamentPlayerId;
    }
}
