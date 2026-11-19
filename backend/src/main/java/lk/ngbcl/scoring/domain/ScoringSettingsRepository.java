package lk.ngbcl.scoring.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoringSettingsRepository extends JpaRepository<ScoringSettings, UUID> {
}
