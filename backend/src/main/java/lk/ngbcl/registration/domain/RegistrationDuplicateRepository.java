package lk.ngbcl.registration.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationDuplicateRepository extends JpaRepository<RegistrationDuplicate, UUID> {

    List<RegistrationDuplicate> findByRegistrationIdOrderByCreatedAtAsc(UUID registrationId);

    long countByRegistrationIdAndResolution(UUID registrationId, DuplicateResolution resolution);
}
