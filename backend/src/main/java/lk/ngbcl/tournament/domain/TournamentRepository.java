package lk.ngbcl.tournament.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentRepository extends JpaRepository<Tournament, UUID> {

    Optional<Tournament> findByCurrentEditionTrue();
}
