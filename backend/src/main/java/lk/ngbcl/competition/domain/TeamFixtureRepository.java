package lk.ngbcl.competition.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamFixtureRepository extends JpaRepository<TeamFixture, UUID> {

    List<TeamFixture> findByTournamentIdOrderByFixtureNumberAsc(UUID tournamentId);

    @Query("select coalesce(max(f.fixtureNumber), 0) from TeamFixture f where f.tournamentId = :t")
    int maxNumber(@Param("t") UUID tournamentId);
}
