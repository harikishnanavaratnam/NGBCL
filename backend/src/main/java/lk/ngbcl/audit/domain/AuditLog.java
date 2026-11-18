package lk.ngbcl.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only record of an important action (SRS REP-005/006). Never updated or deleted. */
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog extends UuidEntity {

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_label", nullable = false, length = 160)
    private String actorLabel;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 80)
    private String entityId;

    @Column(name = "tournament_id")
    private UUID tournamentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private Map<String, Object> afterState;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    protected AuditLog() {
    }

    public AuditLog(Instant occurredAt, UUID actorId, String actorLabel, String action, String entityType,
            String entityId, UUID tournamentId, Map<String, Object> beforeState, Map<String, Object> afterState,
            String reason, String ipHash) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.tournamentId = tournamentId;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.reason = reason;
        this.ipHash = ipHash;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public Map<String, Object> getBeforeState() {
        return beforeState;
    }

    public Map<String, Object> getAfterState() {
        return afterState;
    }

    public String getReason() {
        return reason;
    }
}
