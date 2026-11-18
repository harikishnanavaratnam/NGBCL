package lk.ngbcl.registration.service;

import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.files.domain.FileAsset;
import lk.ngbcl.files.domain.FilePurpose;
import lk.ngbcl.files.domain.VerificationStatus;
import lk.ngbcl.files.service.FileAssetView;
import lk.ngbcl.files.service.FileService;
import lk.ngbcl.registration.domain.RegistrationKind;
import lk.ngbcl.registration.domain.RegistrationNoteRepository;
import lk.ngbcl.registration.domain.RegistrationStatus;
import lk.ngbcl.registration.domain.TeamRegistration;
import lk.ngbcl.registration.domain.TeamRegistrationRepository;
import lk.ngbcl.registration.service.RegistrationDtos.Blocker;
import lk.ngbcl.registration.service.RegistrationDtos.TeamRegistrationDetail;
import lk.ngbcl.registration.service.RegistrationDtos.TeamRegistrationRow;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.PageResponse;
import lk.ngbcl.team.service.TeamService;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff review of team applications (TEM-006..009). Approval creates the team. */
@Service
public class TeamReviewService {

    private final TeamRegistrationRepository registrations;
    private final RegistrationNoteRepository notes;
    private final TeamService teams;
    private final FileService files;
    private final TournamentService tournaments;
    private final AuditService audit;
    private final Clock clock;

    public TeamReviewService(TeamRegistrationRepository registrations, RegistrationNoteRepository notes,
            TeamService teams, FileService files, TournamentService tournaments, AuditService audit, Clock clock) {
        this.registrations = registrations;
        this.notes = notes;
        this.teams = teams;
        this.files = files;
        this.tournaments = tournaments;
        this.audit = audit;
        this.clock = clock;
    }

    public record Filter(RegistrationStatus status, String q, int page, int size) {
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REGISTRATION_VIEW')")
    public PageResponse<TeamRegistrationRow> list(Filter f) {
        UUID t = tournaments.currentTournamentId();
        Specification<TeamRegistration> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("tournamentId"), t));
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("teamName")), like), cb.like(cb.lower(root.get("contactName")), like),
                        cb.like(cb.lower(root.get("contactEmail")), like), cb.like(cb.lower(root.get("registrationNumber")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Sort sort = f.status() == RegistrationStatus.PENDING
                ? Sort.by("submittedAt").ascending().and(Sort.by("id"))
                : Sort.by("submittedAt").descending().and(Sort.by("id"));
        Page<TeamRegistration> page = registrations.findAll(spec,
                PageRequest.of(Math.max(0, f.page()), Math.clamp(f.size(), 1, 100), sort));
        Map<UUID, FileAssetView> logos = files.views(page.getContent().stream().map(TeamRegistration::getLogoAssetId).toList());
        return PageResponse.of(page, r -> {
            FileAssetView logo = logos.get(r.getLogoAssetId());
            return new TeamRegistrationRow(r.getId(), r.getRegistrationNumber(), r.getTeamName(), r.getContactName(),
                    r.getContactEmail(), r.getContactPhone(), r.getStatus(),
                    logo == null ? null : logo.verificationStatus(), logo == null ? null : logo.previewUrl(),
                    r.getStatus() == RegistrationStatus.PENDING && teams.nameTaken(t, r.getTeamName(), null),
                    r.getSubmittedAt(), r.getVersion());
        });
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REGISTRATION_VIEW')")
    public TeamRegistrationDetail get(UUID id) {
        return detail(load(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('REGISTRATION_REVIEW')")
    public TeamRegistrationDetail verifyLogo(UUID id, boolean approve, String reason) {
        TeamRegistration r = load(id);
        requirePending(r);
        files.verify(r.getLogoAssetId(), approve, reason);
        return respond(r);
    }

    @Transactional
    @PreAuthorize("hasAuthority('REGISTRATION_REVIEW')")
    public TeamRegistrationDetail approve(UUID id, String shortCode, Short colorSlot, Long version) {
        TeamRegistration r = load(id);
        checkVersion(r, version);
        requirePending(r);
        List<Blocker> blockers = blockers(r);
        if (!blockers.isEmpty()) {
            throw ApiException.unprocessable(blockers.get(0).code(), blockers.get(0).message());
        }
        UUID teamId = teams.createFromRegistration(r.getTournamentId(), r.getId(),
                new TeamService.TeamInput(r.getTeamName(), shortCode, colorSlot, r.getDescription(), r.getLogoAssetId(),
                        r.getContactName(), r.getContactEmail(), r.getContactPhone()));
        r.approve(teamId, CurrentUser.requireId(), clock.instant());
        audit.record(AuditEvent.of("registration.team.approved", "TeamRegistration", r.getId())
                .inTournament(r.getTournamentId())
                .withChange(Map.of("status", "PENDING"), Map.of("status", "APPROVED", "teamId", teamId.toString())));
        return respond(r);
    }

    @Transactional
    @PreAuthorize("hasAuthority('REGISTRATION_REVIEW')")
    public TeamRegistrationDetail reject(UUID id, String reason, String message, Long version) {
        TeamRegistration r = load(id);
        checkVersion(r, version);
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "Choose or enter a reason.");
        }
        r.reject(reason.trim(), message == null || message.isBlank() ? null : message.trim(), CurrentUser.requireId(),
                clock.instant());
        audit.record(AuditEvent.of("registration.team.rejected", "TeamRegistration", r.getId())
                .inTournament(r.getTournamentId())
                .withChange(Map.of("status", "PENDING"), Map.of("status", "REJECTED")).withReason(reason.trim()));
        return respond(r);
    }

    @Transactional
    @PreAuthorize("hasAuthority('REGISTRATION_REVIEW')")
    public TeamRegistrationDetail addNote(UUID id, String body) {
        TeamRegistration r = load(id);
        PlayerReviewService.addNote(notes, RegistrationKind.TEAM, r.getId(), body, clock.instant());
        return respond(r);
    }

    private List<Blocker> blockers(TeamRegistration r) {
        List<Blocker> out = new ArrayList<>();
        if (r.getStatus() != RegistrationStatus.PENDING) {
            return out;
        }
        FileAsset logo = files.require(r.getLogoAssetId(), FilePurpose.TEAM_LOGO);
        if (logo.getVerificationStatus() != VerificationStatus.VERIFIED) {
            out.add(new Blocker("LOGO_NOT_VERIFIED", logo.getVerificationStatus() == VerificationStatus.REJECTED
                    ? "The logo was rejected. Reject the application or ask for a new logo."
                    : "Check and verify the logo before approving."));
        }
        if (teams.nameTaken(r.getTournamentId(), r.getTeamName(), null)) {
            out.add(new Blocker("TEAM_NAME_TAKEN", "A team with this name already exists."));
        }
        return out;
    }

    private TeamRegistrationDetail respond(TeamRegistration r) {
        registrations.flush();
        return detail(r);
    }

    private TeamRegistrationDetail detail(TeamRegistration r) {
        boolean pending = r.getStatus() == RegistrationStatus.PENDING;
        return new TeamRegistrationDetail(r.getId(), r.getRegistrationNumber(), r.getStatus(), r.getTeamName(),
                r.getDescription(), r.getContactName(), r.getContactEmail(), r.getContactPhone(),
                files.get(r.getLogoAssetId()),
                pending && teams.nameTaken(r.getTournamentId(), r.getTeamName(), null),
                pending ? teams.suggestShortCode(r.getTournamentId(), r.getTeamName()) : null, r.getTeamId(),
                r.getSubmittedAt(), r.getReviewedAt(), r.getRejectionReason(), r.getRejectionMessage(),
                PlayerReviewService.notes(notes, RegistrationKind.TEAM, r.getId()), blockers(r), r.getVersion());
    }

    private TeamRegistration load(UUID id) {
        UUID t = tournaments.currentTournamentId();
        return registrations.findById(id).filter(r -> r.getTournamentId().equals(t))
                .orElseThrow(() -> ApiException.notFound("Team application"));
    }

    private static void requirePending(TeamRegistration r) {
        if (r.getStatus() != RegistrationStatus.PENDING) {
            throw ApiException.conflict("REGISTRATION_NOT_PENDING",
                    "This application has already been " + r.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
    }

    private static void checkVersion(TeamRegistration r, Long expected) {
        if (expected != null && !Objects.equals(r.getVersion(), expected)) {
            throw ApiException.conflict("STALE_DATA", "Someone else changed this application. Reload and try again.");
        }
    }
}
