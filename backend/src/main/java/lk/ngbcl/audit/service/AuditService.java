package lk.ngbcl.audit.service;

import java.time.Clock;
import java.util.UUID;
import lk.ngbcl.audit.domain.AuditLog;
import lk.ngbcl.audit.domain.AuditLogRepository;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.shared.web.RequestInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit entries in the SAME transaction as the change they describe, so an action and
 * its audit record succeed or fail together (AUC-034, MAT-009).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final RequestInfo requestInfo;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, RequestInfo requestInfo, Clock clock) {
        this.repository = repository;
        this.requestInfo = requestInfo;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEvent event) {
        CurrentUser.Actor actor = CurrentUser.get().orElse(null);
        save(event, actor == null ? null : actor.id(), actor == null ? "System" : actor.name());
    }

    /** For events where the actor isn't in the security context yet (e.g. sign-in). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(UUID actorId, String actorLabel, AuditEvent event) {
        save(event, actorId, actorLabel);
    }

    private void save(AuditEvent e, UUID actorId, String actorLabel) {
        repository.save(new AuditLog(clock.instant(), actorId, actorLabel, e.action(), e.entityType(), e.entityId(),
                e.tournamentId(), e.before(), e.after(), e.reason(), requestInfo.ipHash()));
    }
}
