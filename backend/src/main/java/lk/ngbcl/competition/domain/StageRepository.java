package lk.ngbcl.competition.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StageRepository extends JpaRepository<Stage, UUID> {

    List<Stage> findByTournamentIdOrderByStageOrderAsc(UUID tournamentId);
}
