package lk.ngbcl.registration.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRegistrationRepository
        extends JpaRepository<PlayerRegistration, UUID>, JpaSpecificationExecutor<PlayerRegistration> {

    // Duplicate candidates (PLY-010). Separate queries keep every parameter non-null for PostgreSQL.
    List<PlayerRegistration> findByTournamentIdAndEmailAndIdNot(UUID tournamentId, String email, UUID self);

    List<PlayerRegistration> findByTournamentIdAndPhoneAndIdNot(UUID tournamentId, String phone, UUID self);

    @Query("""
            select r from PlayerRegistration r
            where r.tournamentId = :t and r.id <> :self and lower(r.fullName) = :name and r.dateOfBirth = :dob
            """)
    List<PlayerRegistration> findByNameAndDob(@Param("t") UUID tournamentId, @Param("self") UUID self,
            @Param("name") String lowerName, @Param("dob") LocalDate dob);

    long countByTournamentIdAndStatus(UUID tournamentId, RegistrationStatus status);

    long countByTournamentIdAndStatusAndDuplicateFlagTrue(UUID tournamentId, RegistrationStatus status);
}
