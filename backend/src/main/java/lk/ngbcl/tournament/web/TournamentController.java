package lk.ngbcl.tournament.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.tournament.domain.Tournament;
import lk.ngbcl.tournament.domain.TournamentRepository;
import lk.ngbcl.tournament.domain.TournamentStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current edition. The public view exposes only publishable fields (PUB-008); the admin view
 * adds status and audit-relevant data. Full settings (registration windows, publication flags)
 * arrive in M1.
 */
@RestController
public class TournamentController {

    private final TournamentRepository repository;
    private final AuditService audit;

    public TournamentController(TournamentRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    /** Public — never add contact details or internal notes here. */
    public record PublicTournamentResponse(String slug, String name, String shortName, String edition,
            String description, LocalDate startsOn, LocalDate endsOn, String venue, String city, TournamentStatus status,
            String timezone, String currency) {

        static PublicTournamentResponse from(Tournament t) {
            return new PublicTournamentResponse(t.getSlug(), t.getName(), t.getShortName(), t.getEdition(),
                    t.getDescription(), t.getStartsOn(), t.getEndsOn(), t.getVenue(), t.getCity(), t.getStatus(),
                    t.getTimezone(), t.getCurrency());
        }
    }

    public record AdminTournamentResponse(UUID id, String slug, String name, String shortName, String edition,
            String description, LocalDate startsOn, LocalDate endsOn, String venue, String city, TournamentStatus status,
            String timezone, String currency, Long version) {

        static AdminTournamentResponse from(Tournament t) {
            return new AdminTournamentResponse(t.getId(), t.getSlug(), t.getName(), t.getShortName(), t.getEdition(),
                    t.getDescription(), t.getStartsOn(), t.getEndsOn(), t.getVenue(), t.getCity(), t.getStatus(),
                    t.getTimezone(), t.getCurrency(), t.getVersion());
        }
    }

    public record UpdateTournamentRequest(@NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 40) String shortName,
            @Size(max = 4000) String description, LocalDate startsOn, LocalDate endsOn, @Size(max = 200) String venue,
            @Size(max = 80) String city, Long version) {
    }

    @GetMapping("/api/public/tournament")
    @Transactional(readOnly = true)
    public ResponseEntity<PublicTournamentResponse> current() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(60)).cachePublic())
                .body(PublicTournamentResponse.from(load()));
    }

    @GetMapping("/api/admin/tournament")
    @PreAuthorize("hasAuthority('TOURNAMENT_VIEW')")
    @Transactional(readOnly = true)
    public AdminTournamentResponse adminView() {
        return AdminTournamentResponse.from(load());
    }

    @PutMapping("/api/admin/tournament")
    @PreAuthorize("hasAuthority('TOURNAMENT_MANAGE')")
    @Transactional
    public AdminTournamentResponse update(@Valid @RequestBody UpdateTournamentRequest body) {
        Tournament t = load();
        if (body.version() != null && !body.version().equals(t.getVersion())) {
            throw ApiException.conflict("STALE_DATA", "Someone else changed the tournament. Reload and try again.");
        }
        Map<String, Object> before = snapshot(t);
        t.updateDetails(body.name(), body.shortName(), body.description(), body.startsOn(), body.endsOn(), body.venue(),
                body.city());
        audit.record(AuditEvent.of("tournament.updated", "Tournament", t.getId()).inTournament(t.getId())
                .withChange(before, snapshot(t)));
        return AdminTournamentResponse.from(t);
    }

    private Tournament load() {
        return repository.findByCurrentEditionTrue().orElseThrow(() -> ApiException.notFound("Tournament"));
    }

    private static Map<String, Object> snapshot(Tournament t) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", t.getName());
        m.put("shortName", t.getShortName());
        m.put("startsOn", t.getStartsOn() == null ? null : t.getStartsOn().toString());
        m.put("endsOn", t.getEndsOn() == null ? null : t.getEndsOn().toString());
        m.put("venue", t.getVenue());
        m.put("city", t.getCity());
        return m;
    }
}
