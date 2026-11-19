package lk.ngbcl.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.domain.AuditedEntity;

/** A team's purse in the auction (AUC-006, AUC-031). DB CHECKs keep it balanced and non-negative. */
@Entity
@Table(name = "team_budgets")
public class TeamBudget extends AuditedEntity {

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "initial_budget", nullable = false)
    private long initialBudget;

    @Column(name = "adjustments", nullable = false)
    private long adjustments;

    @Column(name = "spent", nullable = false)
    private long spent;

    @Column(name = "remaining", nullable = false)
    private long remaining;

    @Column(name = "squad_count", nullable = false)
    private int squadCount;

    protected TeamBudget() {
    }

    public TeamBudget(UUID auctionId, UUID teamId, long initialBudget) {
        this.auctionId = auctionId;
        this.teamId = teamId;
        this.initialBudget = initialBudget;
        this.remaining = initialBudget;
    }

    public void buy(long price) {
        if (price > remaining) {
            throw ApiException.unprocessable("EXCEEDS_BUDGET", "The price is more than the team's remaining budget.");
        }
        spent += price;
        remaining -= price;
        squadCount++;
    }

    public void refund(long price) {
        spent -= price;
        remaining += price;
        squadCount--;
    }

    public void adjust(long amount) {
        if (remaining + amount < 0) {
            throw ApiException.unprocessable("EXCEEDS_BUDGET", "That would make the remaining budget negative.");
        }
        adjustments += amount;
        remaining += amount;
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public long getInitialBudget() {
        return initialBudget;
    }

    public long getAdjustments() {
        return adjustments;
    }

    public long getSpent() {
        return spent;
    }

    public long getRemaining() {
        return remaining;
    }

    public int getSquadCount() {
        return squadCount;
    }
}
