package lk.ngbcl.competition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;

/** One match slot in every tie, in playing order (e.g. 1 = MS, 2 = WS …). */
@Entity
@Table(name = "fixture_template_items")
public class FixtureTemplateItem extends UuidEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    protected FixtureTemplateItem() {
    }

    public FixtureTemplateItem(UUID tournamentId, int position, UUID categoryId) {
        this.tournamentId = tournamentId;
        this.position = position;
        this.categoryId = categoryId;
    }

    public int getPosition() {
        return position;
    }

    public UUID getCategoryId() {
        return categoryId;
    }
}
