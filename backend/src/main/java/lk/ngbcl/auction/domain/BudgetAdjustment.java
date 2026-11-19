package lk.ngbcl.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;
import org.hibernate.annotations.Immutable;

/** Manual budget correction (transaction rules: needs permission + audit). */
@Entity
@Immutable
@Table(name = "budget_adjustments")
public class BudgetAdjustment extends UuidEntity {

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "reason", nullable = false, length = 300)
    private String reason;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BudgetAdjustment() {
    }

    public BudgetAdjustment(UUID budgetId, long amount, String reason, UUID actorId, Instant at) {
        this.budgetId = budgetId;
        this.amount = amount;
        this.reason = reason;
        this.actorId = actorId;
        this.createdAt = at;
    }
}
