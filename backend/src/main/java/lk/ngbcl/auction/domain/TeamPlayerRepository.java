package lk.ngbcl.auction.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamPlayerRepository extends JpaRepository<TeamPlayer, UUID> {

    List<TeamPlayer> findByTournamentIdAndActiveTrue(UUID tournamentId);

    List<TeamPlayer> findByTeamIdAndActiveTrue(UUID teamId);

    Optional<TeamPlayer> findByTournamentIdAndTournamentPlayerIdAndActiveTrue(UUID tournamentId, UUID tournamentPlayerId);
}
