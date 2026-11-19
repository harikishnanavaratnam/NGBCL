package lk.ngbcl.competition.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    List<Match> findByFixtureIdInOrderByMatchOrderAsc(Collection<UUID> fixtureIds);

    List<Match> findByFixtureIdOrderByMatchOrderAsc(UUID fixtureId);

    /** Serialises every scoring command for one match. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :id")
    java.util.Optional<Match> lock(@Param("id") UUID id);

    List<Match> findByScorerUserIdOrderByScheduledAtAsc(UUID scorerUserId);

    List<Match> findByStatusIn(Collection<MatchStatus> statuses);
}
