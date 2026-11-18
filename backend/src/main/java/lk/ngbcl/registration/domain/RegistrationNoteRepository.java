package lk.ngbcl.registration.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationNoteRepository extends JpaRepository<RegistrationNote, UUID> {

    List<RegistrationNote> findByRegistrationKindAndRegistrationIdOrderByCreatedAtAsc(RegistrationKind kind, UUID id);
}
