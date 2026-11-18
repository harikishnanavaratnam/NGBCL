package lk.ngbcl.audit.service;

import java.util.Map;
import java.util.UUID;

/**
 * What happened. Use past-tense dotted actions ("user.created", "auction.lot.sold") and keep
 * before/after to the fields that changed — never secrets or password hashes.
 */
public record AuditEvent(
        String action,
        String entityType,
        String entityId,
        UUID tournamentId,
        Map<String, Object> before,
        Map<String, Object> after,
        String reason) {

    public static AuditEvent of(String action, String entityType, Object entityId) {
        return new AuditEvent(action, entityType, entityId == null ? null : entityId.toString(), null, null, null, null);
    }

    public AuditEvent withChange(Map<String, Object> before, Map<String, Object> after) {
        return new AuditEvent(action, entityType, entityId, tournamentId, before, after, reason);
    }

    public AuditEvent withReason(String reason) {
        return new AuditEvent(action, entityType, entityId, tournamentId, before, after, reason);
    }

    public AuditEvent inTournament(UUID tournamentId) {
        return new AuditEvent(action, entityType, entityId, tournamentId, before, after, reason);
    }
}
