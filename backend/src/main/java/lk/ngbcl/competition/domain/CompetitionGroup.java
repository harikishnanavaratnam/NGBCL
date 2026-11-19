package lk.ngbcl.competition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;

@Entity
@Table(name = "competition_groups")
public class CompetitionGroup extends UuidEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "group_order", nullable = false)
    private int groupOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CompetitionGroup() {
    }

    public CompetitionGroup(UUID tournamentId, String name, int order, Instant at) {
        this.tournamentId = tournamentId;
        this.name = name;
        this.groupOrder = order;
        this.createdAt = at;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public String getName() {
        return name;
    }

    public int getGroupOrder() {
        return groupOrder;
    }
}
