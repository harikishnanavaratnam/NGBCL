package lk.ngbcl.team.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByTournamentIdOrderByNameAsc(UUID tournamentId);

    @Query("select count(t) from Team t where t.tournamentId = :t and lower(t.name) = lower(:name) and t.id <> :exclude")
    long countWithName(@Param("t") UUID tournamentId, @Param("name") String name, @Param("exclude") UUID exclude);

    @Query("select count(t) from Team t where t.tournamentId = :t and t.shortCode = :code and t.id <> :exclude")
    long countWithCode(@Param("t") UUID tournamentId, @Param("code") String code, @Param("exclude") UUID exclude);

    @Query("select count(t) from Team t where t.tournamentId = :t and t.colorSlot = :slot and t.id <> :exclude")
    long countWithColor(@Param("t") UUID tournamentId, @Param("slot") Short slot, @Param("exclude") UUID exclude);

    boolean existsByTournamentIdAndSlug(UUID tournamentId, String slug);
}
