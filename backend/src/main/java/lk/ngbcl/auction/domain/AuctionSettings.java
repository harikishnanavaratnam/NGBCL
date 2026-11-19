package lk.ngbcl.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Auction rules for one edition (AUC-002..005). All values are admin-configurable. */
@Entity
@Table(name = "auction_settings")
public class AuctionSettings {

    @Id
    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "initial_budget", nullable = false)
    private long initialBudget = 1_000_000;

    @Column(name = "max_squad_size", nullable = false)
    private int maxSquadSize = 8;

    @Column(name = "min_squad_size")
    private Integer minSquadSize;

    @Column(name = "default_base_price", nullable = false)
    private long defaultBasePrice = 10_000;

    @Column(name = "min_increment", nullable = false)
    private long minIncrement = 5_000;

    @Column(name = "allow_reauction", nullable = false)
    private boolean allowReauction = true;

    @Column(name = "allow_manual_adjustment", nullable = false)
    private boolean allowManualAdjustment = true;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AuctionSettings() {
    }

    public AuctionSettings(UUID tournamentId) {
        this.tournamentId = tournamentId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void update(long initialBudget, int maxSquadSize, Integer minSquadSize, long defaultBasePrice,
            long minIncrement, boolean allowReauction, boolean allowManualAdjustment, Instant startsAt) {
        this.initialBudget = initialBudget;
        this.maxSquadSize = maxSquadSize;
        this.minSquadSize = minSquadSize;
        this.defaultBasePrice = defaultBasePrice;
        this.minIncrement = minIncrement;
        this.allowReauction = allowReauction;
        this.allowManualAdjustment = allowManualAdjustment;
        this.startsAt = startsAt;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public long getInitialBudget() {
        return initialBudget;
    }

    public int getMaxSquadSize() {
        return maxSquadSize;
    }

    public Integer getMinSquadSize() {
        return minSquadSize;
    }

    public long getDefaultBasePrice() {
        return defaultBasePrice;
    }

    public long getMinIncrement() {
        return minIncrement;
    }

    public boolean isAllowReauction() {
        return allowReauction;
    }

    public boolean isAllowManualAdjustment() {
        return allowManualAdjustment;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Long getVersion() {
        return version;
    }
}
