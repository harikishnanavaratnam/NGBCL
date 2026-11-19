package lk.ngbcl.competition.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompetitionSettingsRepository extends JpaRepository<CompetitionSettings, UUID> {

    /** Creates the edition's settings once; returns 1 only for the call that created them. */
    @Modifying
    @Query(value = """
            INSERT INTO competition_settings (tournament_id, updated_at) VALUES (:t, now())
            ON CONFLICT (tournament_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfMissing(@Param("t") UUID tournamentId);
}
