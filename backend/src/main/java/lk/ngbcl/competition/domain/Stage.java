package lk.ngbcl.competition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;

@Entity
@Table(name = "stages")
public class Stage extends UuidEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 12)
    private StageType type;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "stage_order", nullable = false)
    private int stageOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Stage() {
    }

    public Stage(UUID tournamentId, StageType type, String name, int order, Instant at) {
        this.tournamentId = tournamentId;
        this.type = type;
        this.name = name;
        this.stageOrder = order;
        this.createdAt = at;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public StageType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getStageOrder() {
        return stageOrder;
    }
}
