package lk.ngbcl.registration.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRegistrationRepository
        extends JpaRepository<TeamRegistration, UUID>, JpaSpecificationExecutor<TeamRegistration> {

    /** Pending applications already using this name (names must be unique, TEM-011). */
    @Query("""
            select count(r) from TeamRegistration r where r.tournamentId = :t
              and r.status = lk.ngbcl.registration.domain.RegistrationStatus.PENDING and lower(r.teamName) = lower(:name)
              and r.id <> :exclude
            """)
    long countPendingWithName(@Param("t") UUID tournamentId, @Param("name") String name, @Param("exclude") UUID exclude);

    long countByTournamentIdAndStatus(UUID tournamentId, RegistrationStatus status);
}
