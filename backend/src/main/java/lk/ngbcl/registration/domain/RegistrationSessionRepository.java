package lk.ngbcl.registration.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationSessionRepository extends JpaRepository<RegistrationSession, UUID> {

    Optional<RegistrationSession> findByTokenHash(String tokenHash);

    long countByIpHashAndCreatedAtAfter(String ipHash, Instant after);
}
