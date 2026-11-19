package lk.ngbcl.auction.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetAdjustmentRepository extends JpaRepository<BudgetAdjustment, UUID> {
}
