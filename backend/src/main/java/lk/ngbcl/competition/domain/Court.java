package lk.ngbcl.competition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

@Entity
@Table(name = "courts")
public class Court extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Court() {
    }

    public Court(UUID tournamentId, String name, int order) {
        this.tournamentId = tournamentId;
        this.name = name;
        this.displayOrder = order;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public String getName() {
        return name;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }
}
