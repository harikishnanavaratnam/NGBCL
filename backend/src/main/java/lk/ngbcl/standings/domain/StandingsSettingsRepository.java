package lk.ngbcl.standings.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandingsSettingsRepository extends JpaRepository<StandingsSettings, UUID> {
}
