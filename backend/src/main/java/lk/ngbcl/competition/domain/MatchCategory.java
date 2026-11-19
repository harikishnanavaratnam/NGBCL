package lk.ngbcl.competition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

/** A kind of individual match, e.g. MS (men's singles) or XD (mixed doubles) — FIX-007. */
@Entity
@Table(name = "match_categories")
public class MatchCategory extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "code", nullable = false, length = 8)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "players_per_side", nullable = false)
    private short playersPerSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender_rule", nullable = false, length = 10)
    private GenderRule genderRule = GenderRule.ANY;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected MatchCategory() {
    }

    public MatchCategory(UUID tournamentId, String code, String name, int playersPerSide, GenderRule rule, int order) {
        this.tournamentId = tournamentId;
        this.displayOrder = order;
        update(code, name, playersPerSide, rule);
    }

    public void update(String code, String name, int playersPerSide, GenderRule rule) {
        this.code = code;
        this.name = name;
        this.playersPerSide = (short) playersPerSide;
        this.genderRule = rule;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getPlayersPerSide() {
        return playersPerSide;
    }

    public GenderRule getGenderRule() {
        return genderRule;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }
}
