package lk.ngbcl.player.category;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.files.service.UploadContext;
import lk.ngbcl.shared.domain.Ids;
import lk.ngbcl.shared.web.ApiException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final PlayerCategoryRepository repository;
    private final UploadContext tournament;
    private final AuditService audit;

    public CategoryService(PlayerCategoryRepository repository, UploadContext tournament, AuditService audit) {
        this.repository = repository;
        this.tournament = tournament;
        this.audit = audit;
    }

    /** Admin view. playerCount is filled from registrations from M2 onward. */
    public record CategoryResponse(UUID id, String name, String description, boolean active, int displayOrder,
            long playerCount, long version) {

        static CategoryResponse from(PlayerCategory c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive(), c.getDisplayOrder(), 0,
                    c.getVersion() == null ? 0 : c.getVersion());
        }
    }

    /** Public view for the registration form — active only, no internal fields. */
    public record PublicCategory(UUID id, String name, String description) {
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('CATEGORY_MANAGE', 'REGISTRATION_VIEW')")
    public List<CategoryResponse> list() {
        return repository.findByTournamentIdOrderByDisplayOrderAscNameAsc(tournament.currentTournamentId()).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicCategory> listActivePublic() {
        return repository.findByTournamentIdAndActiveTrueOrderByDisplayOrderAscNameAsc(tournament.currentTournamentId())
                .stream()
                .map(c -> new PublicCategory(c.getId(), c.getName(), c.getDescription()))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public CategoryResponse create(String name, String description) {
        UUID t = tournament.currentTournamentId();
        requireUniqueName(t, name, Ids.newId());
        PlayerCategory c = new PlayerCategory(t, name, description, repository.maxOrder(t) + 1);
        repository.saveAndFlush(c);
        audit.record(AuditEvent.of("category.created", "PlayerCategory", c.getId()).inTournament(t)
                .withChange(null, Map.of("name", c.getName())));
        return CategoryResponse.from(c);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public CategoryResponse update(UUID id, String name, String description, Boolean active, Long version) {
        PlayerCategory c = load(id);
        if (version != null && !Objects.equals(c.getVersion(), version)) {
            throw ApiException.conflict("STALE_DATA", "Someone else changed this category. Reload and try again.");
        }
        Map<String, Object> before = snapshot(c);
        if (name != null) {
            requireUniqueName(c.getTournamentId(), name, c.getId());
            c.edit(name, description);
        }
        if (active != null) {
            c.setActive(active);
        }
        audit.record(AuditEvent.of("category.updated", "PlayerCategory", c.getId()).inTournament(c.getTournamentId())
                .withChange(before, snapshot(c)));
        repository.flush(); // bump @Version now so the response carries the version the client must send next
        return CategoryResponse.from(c);
    }

    /** Full new order (CAT-004). Must list every category of the tournament exactly once. */
    @Transactional
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public List<CategoryResponse> reorder(List<UUID> orderedIds) {
        UUID t = tournament.currentTournamentId();
        List<PlayerCategory> all = repository.findByTournamentIdOrderByDisplayOrderAscNameAsc(t);
        if (orderedIds == null || orderedIds.size() != all.size() || new HashSet<>(orderedIds).size() != all.size()) {
            throw ApiException.badRequest("ORDER_INCOMPLETE", "Send every category exactly once.");
        }
        Map<UUID, PlayerCategory> byId = new java.util.HashMap<>();
        all.forEach(c -> byId.put(c.getId(), c));
        List<String> before = all.stream().map(PlayerCategory::getName).toList();
        List<PlayerCategory> ordered = new ArrayList<>();
        for (UUID id : orderedIds) {
            PlayerCategory c = byId.get(id);
            if (c == null) {
                throw ApiException.badRequest("ORDER_INCOMPLETE", "Unknown category in the new order.");
            }
            ordered.add(c);
        }
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo(i + 1);
        }
        audit.record(AuditEvent.of("category.reordered", "PlayerCategory", null).inTournament(t)
                .withChange(Map.of("order", before), Map.of("order", ordered.stream().map(PlayerCategory::getName).toList())));
        repository.flush();
        return ordered.stream().map(CategoryResponse::from).toList();
    }

    private void requireUniqueName(UUID tournamentId, String name, UUID exclude) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("NAME_REQUIRED", "Give the category a name.");
        }
        if (repository.countSameName(tournamentId, name.trim(), exclude) > 0) {
            throw ApiException.conflict("CATEGORY_NAME_TAKEN", "A category with this name already exists.");
        }
    }

    private PlayerCategory load(UUID id) {
        PlayerCategory c = repository.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        if (!c.getTournamentId().equals(tournament.currentTournamentId())) {
            throw ApiException.notFound("Category");
        }
        return c;
    }

    private static Map<String, Object> snapshot(PlayerCategory c) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("name", c.getName());
        m.put("description", c.getDescription());
        m.put("active", c.isActive());
        return m;
    }
}
