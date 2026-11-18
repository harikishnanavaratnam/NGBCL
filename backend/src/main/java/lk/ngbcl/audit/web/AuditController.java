package lk.ngbcl.audit.web;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.ngbcl.audit.domain.AuditLog;
import lk.ngbcl.audit.domain.AuditLogRepository;
import lk.ngbcl.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    public record AuditEntryResponse(UUID id, Instant occurredAt, UUID actorId, String actorName, String action,
            String entityType, String entityId, Map<String, Object> before, Map<String, Object> after, String reason) {

        static AuditEntryResponse from(AuditLog a) {
            return new AuditEntryResponse(a.getId(), a.getOccurredAt(), a.getActorId(), a.getActorLabel(), a.getAction(),
                    a.getEntityType(), a.getEntityId(), a.getBeforeState(), a.getAfterState(), a.getReason());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> list(@RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId, @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (entityType != null && !entityType.isBlank()) {
                p.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null && !entityId.isBlank()) {
                p.add(cb.equal(root.get("entityId"), entityId));
            }
            if (actorId != null) {
                p.add(cb.equal(root.get("actorId"), actorId));
            }
            if (action != null && !action.isBlank()) {
                p.add(cb.like(root.get("action"), action + "%"));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        int safeSize = Math.min(Math.max(size, 1), 100);
        var result = repository.findAll(spec,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "occurredAt")));
        return PageResponse.of(result, AuditEntryResponse::from);
    }
}
