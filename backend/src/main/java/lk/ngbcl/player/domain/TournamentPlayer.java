package lk.ngbcl.player.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;
import lk.ngbcl.shared.web.ApiException;

/** An approved player in one edition. Withdrawn / inactive instead of deleted (PLY-027, BR-019). */
@Entity
@Table(name = "tournament_players")
public class TournamentPlayer extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "registration_id", nullable = false)
    private UUID registrationId;

    @Column(name = "skill_category_id")
    private UUID skillCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TournamentPlayerStatus status = TournamentPlayerStatus.APPROVED;

    @Column(name = "status_reason", length = 300)
    private String statusReason;

    protected TournamentPlayer() {
    }

    public TournamentPlayer(UUID tournamentId, UUID playerId, UUID registrationId, UUID skillCategoryId) {
        this.tournamentId = tournamentId;
        this.playerId = playerId;
        this.registrationId = registrationId;
        this.skillCategoryId = skillCategoryId;
    }

    public void changeStatus(TournamentPlayerStatus next, String reason) {
        if (next == status) {
            return;
        }
        if (next != TournamentPlayerStatus.APPROVED && (reason == null || reason.isBlank())) {
            throw ApiException.badRequest("REASON_REQUIRED", "Give a reason — it's kept in the player's history.");
        }
        this.status = next;
        this.statusReason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public void changeCategory(UUID categoryId) {
        this.skillCategoryId = categoryId;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getRegistrationId() {
        return registrationId;
    }

    public UUID getSkillCategoryId() {
        return skillCategoryId;
    }

    public TournamentPlayerStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }
}
