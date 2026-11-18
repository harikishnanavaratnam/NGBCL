package lk.ngbcl.player.category;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerCategoryRepository extends JpaRepository<PlayerCategory, UUID> {

    List<PlayerCategory> findByTournamentIdOrderByDisplayOrderAscNameAsc(UUID tournamentId);

    List<PlayerCategory> findByTournamentIdAndActiveTrueOrderByDisplayOrderAscNameAsc(UUID tournamentId);

    @Query("select count(c) from PlayerCategory c where c.tournamentId = :t and lower(c.name) = lower(:name) and c.id <> :exclude")
    long countSameName(@Param("t") UUID tournamentId, @Param("name") String name, @Param("exclude") UUID exclude);

    @Query("select coalesce(max(c.displayOrder), 0) from PlayerCategory c where c.tournamentId = :t")
    int maxOrder(@Param("t") UUID tournamentId);
}
