package lk.ngbcl.scoring.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoreEventRepository extends JpaRepository<ScoreEvent, UUID> {

    List<ScoreEvent> findByMatchIdOrderBySeqAsc(UUID matchId);

    Optional<ScoreEvent> findByMatchIdAndClientEventId(UUID matchId, String clientEventId);

    @Query("select coalesce(max(e.seq), 0) from ScoreEvent e where e.matchId = :m")
    int maxSeq(@Param("m") UUID matchId);
}
