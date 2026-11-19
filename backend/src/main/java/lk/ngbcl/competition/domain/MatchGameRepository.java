package lk.ngbcl.competition.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchGameRepository extends JpaRepository<MatchGame, UUID> {

    List<MatchGame> findByMatchIdOrderByGameNumberAsc(UUID matchId);

    List<MatchGame> findByMatchIdIn(Collection<UUID> matchIds);

    @Modifying
    @Query("delete from MatchGame g where g.matchId = :m")
    void deleteByMatch(@Param("m") UUID matchId);
}
