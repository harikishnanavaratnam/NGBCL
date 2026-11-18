package lk.ngbcl.tournament.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentSettingsRepository extends JpaRepository<TournamentSettings, UUID> {
}
