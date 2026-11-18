package lk.ngbcl.content.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommitteeGroupRepository extends JpaRepository<CommitteeGroup, UUID> {

    List<CommitteeGroup> findByTournamentIdOrderByDisplayOrderAscNameAsc(UUID tournamentId);

    @Query("select coalesce(max(g.displayOrder), 0) from CommitteeGroup g where g.tournamentId = :t")
    int maxOrder(@Param("t") UUID tournamentId);
}
