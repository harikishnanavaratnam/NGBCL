package lk.ngbcl.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

@Entity
@Table(name = "committee_groups")
public class CommitteeGroup extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "style", nullable = false, length = 20)
    private CommitteeStyle style = CommitteeStyle.COMPACT;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected CommitteeGroup() {
    }

    public CommitteeGroup(UUID tournamentId, String name, CommitteeStyle style, int displayOrder) {
        this.tournamentId = tournamentId;
        this.name = name;
        this.style = style;
        this.displayOrder = displayOrder;
    }

    public void edit(String name, CommitteeStyle style) {
        this.name = name;
        this.style = style;
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

    public CommitteeStyle getStyle() {
        return style;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
