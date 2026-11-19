package lk.ngbcl.competition.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FixtureTemplateItemRepository extends JpaRepository<FixtureTemplateItem, UUID> {

    List<FixtureTemplateItem> findByTournamentIdOrderByPositionAsc(UUID tournamentId);

    @Modifying
    @Query("delete from FixtureTemplateItem i where i.tournamentId = :t")
    void deleteByTournament(@Param("t") UUID tournamentId);
}
