package lk.ngbcl.player.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TournamentPlayerRepository
        extends JpaRepository<TournamentPlayer, UUID>, JpaSpecificationExecutor<TournamentPlayer> {

    Optional<TournamentPlayer> findByTournamentIdAndPlayerId(UUID tournamentId, UUID playerId);

    long countBySkillCategoryIdAndStatus(UUID categoryId, TournamentPlayerStatus status);
}
