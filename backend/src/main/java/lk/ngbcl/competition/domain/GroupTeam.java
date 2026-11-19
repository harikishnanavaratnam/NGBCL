package lk.ngbcl.competition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;

@Entity
@Table(name = "group_teams")
public class GroupTeam extends UuidEntity {

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "seed", nullable = false)
    private int seed;

    protected GroupTeam() {
    }

    public GroupTeam(UUID groupId, UUID teamId, int seed) {
        this.groupId = groupId;
        this.teamId = teamId;
        this.seed = seed;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public int getSeed() {
        return seed;
    }
}
