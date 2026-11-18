package lk.ngbcl.player.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

/** Tournament-specific skill category (CAT-001..006). Deactivated, never deleted (CAT-005). */
@Entity
@Table(name = "player_categories")
public class PlayerCategory extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected PlayerCategory() {
    }

    public PlayerCategory(UUID tournamentId, String name, String description, int displayOrder) {
        this.tournamentId = tournamentId;
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.displayOrder = displayOrder;
    }

    public void edit(String name, String description) {
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void moveTo(int order) {
        this.displayOrder = order;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
