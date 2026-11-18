package lk.ngbcl.registration.service;

import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
import lk.ngbcl.player.category.CategoryService;
import lk.ngbcl.player.service.PlayerService;
import lk.ngbcl.registration.domain.DuplicateResolution;
import lk.ngbcl.registration.domain.PlayerRegistration;
import lk.ngbcl.registration.domain.PlayerRegistration.PlayerDetails;
import lk.ngbcl.registration.domain.PlayerRegistrationRepository;
import lk.ngbcl.registration.domain.RegistrationDuplicate;
import lk.ngbcl.registration.domain.RegistrationDuplicateRepository;
import lk.ngbcl.registration.domain.RegistrationKind;
import lk.ngbcl.registration.domain.RegistrationNote;
import lk.ngbcl.registration.domain.RegistrationNoteRepository;
import lk.ngbcl.registration.domain.RegistrationStatus;
import lk.ngbcl.registration.domain.TeamRegistrationRepository;
import lk.ngbcl.registration.service.RegistrationDtos.Blocker;
import lk.ngbcl.registration.service.RegistrationDtos.BulkResult;
import lk.ngbcl.registration.service.RegistrationDtos.BulkSkip;
import lk.ngbcl.registration.service.RegistrationDtos.CandidateRegistration;
import lk.ngbcl.registration.service.RegistrationDtos.DuplicateView;
import lk.ngbcl.registration.service.RegistrationDtos.NoteView;
import lk.ngbcl.registration.service.RegistrationDtos.PlayerRegistrationDetail;
import lk.ngbcl.registration.service.RegistrationDtos.PlayerRegistrationRow;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.FieldErrors;
import lk.ngbcl.shared.web.PageResponse;
import lk.ngbcl.tournament.domain.TournamentSettings;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Staff review of player registrations (PLY-012..023). */
@Service
public class PlayerReviewService {

    private final PlayerRegistrationRepository registrations;
    private final RegistrationDuplicateRepository duplicates;
    private final TeamRegistrationRepository teamRegs;
    private final RegistrationNoteRepository notes;
    private final DuplicateDetector detector;
    private final PlayerService players;
    private final CategoryService categories;
    private final FileService files;
    private final TournamentService tournaments;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    public PlayerReviewService(PlayerRegistrationRepository registrations, RegistrationDuplicateRepository duplicates,
            TeamRegistrationRepository teamRegs, RegistrationNoteRepository notes, DuplicateDetector detector, PlayerService players,
            CategoryService categories, FileService files, TournamentService tournaments, AuditService audit,
            Clock clock, PlatformTransactionManager txManager) {
        this.registrations = registrations;
        this.duplicates = duplicates;
        this.teamRegs = teamRegs;
        this.notes = notes;
        this.detector = detector;
        this.players = players;
        this.categories = categories;
        this.files = files;
        this.tournaments = tournaments;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---- Queries ----------------------------------------------------------------------

    public record Filter(RegistrationStatus status, String q, Boolean duplicate, UUID categoryId, int page, int size) {
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REGISTRATION_VIEW')")
    public PageResponse<PlayerRegistrationRow> list(Filter f) {
        UUID t = tournaments.currentTournamentId();
        Specification<PlayerRegistration> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("tournamentId"), t));
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.duplicate() != null) {
                p.add(cb.equal(root.get("duplicateFlag"), f.duplicate()));
            }
            if (f.categoryId() != null) {
                p.add(cb.equal(root.get("skillCategoryId"), f.categoryId()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("fullName")), like), cb.like(root.get("email"), like),
                        cb.like(root.get("phone"), like), cb.like(cb.lower(root.get("registrationNumber")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        // Pending is a queue (oldest first); everything else newest first.
        Sort sort = f.status() == RegistrationStatus.PENDING
                ? Sort.by("submittedAt").ascending().and(Sort.by("id"))
                : Sort.by("submittedAt").descending().and(Sort.by("id"));
        int size = Math.clamp(f.size(), 1, 100);
        Page<PlayerRegistration> page = registrations.findAll(spec, PageRequest.of(Math.max(0, f.page()), size, sort));
        Map<UUID, String> cats = categories.namesById(t);
        Map<UUID, FileAssetView> photos = files.views(page.getContent().stream().map(PlayerRegistration::getPhotoAssetId).toList());
        return PageResponse.of(page, r -> {
            FileAssetView photo = photos.get(r.getPhotoAssetId());
            return new PlayerRegistrationRow(r.getId(), r.getRegistrationNumber(), r.getFullName(), r.getEmail(),
                    r.getPhone(), r.getHomeArea(), r.getSkillCategoryId(), cats.get(r.getSkillCategoryId()),
                    r.getStatus(), r.isDuplicateFlag(), photo == null ? null : photo.verificationStatus(),
                    photo == null ? null : photo.previewUrl(), r.getSubmittedAt(), r.getVersion());
        });
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REGISTRATION_VIEW')")
    public PlayerRegistrationDetail get(UUID id) {
        return detail(load(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REGISTRATION_VIEW')")
    public RegistrationDtos.Summary summary() {
        UUID t = tournaments.currentTournamentId();
        return new RegistrationDtos.Summary(
                registrations.countByTournamentIdAndStatus(t, RegistrationStatus.PENDING),
                teamRegs.countByTournamentIdAndStatus(t, RegistrationStatus.PENDING),
                registrations.countByTournamentIdAndStatusAndDuplicateFlagTrue(t, RegistrationStatus.PENDING),
                registrations.countByTournamentIdAndStatus(t, RegistrationStatus.APPROVED),
                teamRegs.countByTournamentIdAndStatus(t, RegistrationStatus.APPROVED),
                registrations.countByTournamentIdAndStatus(t, RegistrationStatus.REJECTED),
                teamRegs.countByTournamentIdAndStatus(t, RegistrationStatus.REJECTED));
    }

    // ---- Review actions -------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('REGISTRATION_REVIEW')")
    public PlayerRegistrationDetail edit(UUID id, PlayerDetails in, Long version) {
        PlayerRegistration r = load(id);
        checkVersion(r, version);
        FieldErrors errors = new FieldErrors();
        String fullName = PublicRegistrationService.name(in.fullName(), "fullName", errors);
        String email = PublicRegistrationService.email(in.email(), "email", errors);
        String phone = PublicRegistrationService.phone(in.phone(), "phone", true, errors);
        String emergencyPhone = PublicRegistrationService.phone(in.emergencyPhone(), "emergencyPhone", false, errors);
        String gender = in.gender() == null || in.gender().isBlank() ? null : in.gender().trim().toUpperCase(Locale.ROOT);
        if (gender != null && !PublicRegistrationService.GENDERS.contains(gender)) {
            errors.add("gender", "Choose an option.");
        }
        if (in.skillCategoryId() != null && !in.skillCategoryId().equals(r.getSkillCategoryId())) {
            try {
                categories.requireActive(r.getTournamentId(), in.skillCategoryId());
            } catch (ApiException e) {
                errors.add("skillCategoryId", e.getMessage());
            }
        }
        errors.throwIfAny();
        Map<String, Object> before = snapshot(r);
        List<Object> identityBefore = identity(r);
        r.edit(new PlayerDetails(fullName, in.dateOfBirth(), gender, email, phone, blank(in.address()),
                blank(in.homeArea()), blank(in.experience()), blank(in.emergencyName()), emergencyPhone,
                in.skillCategoryId()));
        // Contact details may have changed: re-run matching for still-open candidates.
        if (!identityBefore.equals(identity(r))) {
            duplicates.deleteAll(duplicates.findByRegistrationIdOrderByCreatedAtAsc(r.getId()).stream()
                    .filter(d -> d.getResolution() == DuplicateResolution.OPEN).toList());
            duplicates.flush();
            detector.detect(r, tournaments.settingsFor(r.getTournamentId()));
            detector.refreshFlag(r);
        }
        audit.record(AuditEvent.of("registration.player.edited", "PlayerRegistration", r.getId())
                .inTournament(r.getTournamentId()).withChange(before, snapshot(r)));
        return respond(r);
    }

    @Transactional
    @PreAuthorize("hasAuthority('REGISTRATION_REVIEW')")
    public PlayerRegistrationDetail verifyPhoto(UUID id, boolean approve, String reason) {
        PlayerRegistration r = load(id);
        requirePending(r);
        files.verify(r.getPhotoAssetId(), approve, reason);
        return respond(r);
    }

    public enum DuplicateAction {
        LINK_EXISTING,
        NEW_PROFILE,
        NOT_DUPLICATE
    }

    @Transactional
    @PreAuthorize("hasAuthority('REGISTRATION_REVIEW')")
    public PlayerRegistrationDetail resolveDuplicate(UUID id, UUID duplicateId, DuplicateAction action) {
        PlayerRegistration r = load(id);
        requirePending(r);
        RegistrationDuplicate d = duplicates.findById(duplicateId)
                .filter(x -> x.getRegistrationId().equals(r.getId()))
                .orElseThrow(() -> ApiException.notFound("Possible duplicate"));
        if (d.getResolution() != DuplicateResolution.OPEN) {
            throw ApiException.conflict("DUPLICATE_ALREADY_RESOLVED", "This match has already been resolved.");
        }
        UUID by = CurrentUser.requireId();
        Instant now = clock.instant();
        switch (action) {
            case LINK_EXISTING -> {
                UUID playerId = d.getCandidatePlayerId();
                if (playerId == null && d.getCandidateRegistrationId() != null) {
                    playerId = registrations.findById(d.getCandidateRegistrationId())
                            .map(PlayerRegistration::getPlayerId).orElse(null);
                }
                if (playerId == null) {
                    throw ApiException.unprocessable("NOTHING_TO_LINK",
                            "The other registration isn't approved yet, so there's no profile to link. "
                                    + "Review that one first or reject this one as a duplicate.");
                }
                if (players.isEnrolled(r.getTournamentId(), playerId)) {
                    throw ApiException.conflict("ALREADY_IN_TOURNAMENT",
                            "That player is already approved for this edition. Reject this registration as a duplicate.");
                }
                if (r.getLinkedPlayerId() != null && !r.getLinkedPlayerId().equals(playerId)) {
                    throw ApiException.conflict("ALREADY_LINKED", "This registration is already linked to another profile.");
                }
                r.linkTo(playerId);
                d.resolve(DuplicateResolution.LINKED, by, now);
            }
            case NEW_PROFILE -> d.resolve(DuplicateResolution.NEW_PROFILE, by, now);
            case NOT_DUPLICATE -> d.resolve(DuplicateResolution.NOT_DUPLICATE, by, now);
            default -> throw new IllegalStateException();
        }
        detector.refreshFlag(r);
        audit.record(AuditEvent.of("registration.duplicate.resolved", "PlayerRegistration", r.getId())
                .inTournament(r.getTournamentId())
                .withChange(Map.of("duplicateId", d.getId().toString(), "resolution", "OPEN"),
                        Map.of("duplicateId", d.getId().toString(), "resolution", d.getResolution().name())));
        return respond(r);
    }

    @Transactional
    @PreAuthorize("hasAuthority('REGISTRATION_REVIEW')")
    public PlayerRegistrationDetail approve(UUID id, Long version) {
        PlayerRegistration r = load(id);
        checkVersion(r, version);
        doApprove(r);
        return respond(r);
    }

    @PreAuthorize("hasAuthority('REGISTRATION_REVIEW')")
    public BulkResult bulkApprove(List<UUID> ids) {
        if (ids.size() > 100) {
            throw ApiException.badRequest("TOO_MANY", "Approve at most 100 registrations at a time.");
        }
        List<UUID> approved = new ArrayList<>();
        List<BulkSkip> skipped = new ArrayList<>();
        for (UUID id : ids.stream().distinct().toList()) {
            try {
                tx.executeWithoutResult(s -> doApprove(load(id)));
                approved.add(id);
            } catch (ApiException e) {
                String number = registrations.findById(id).map(PlayerRegistration::getRegistrationNumber).orElse(null);
                skipped.add(new BulkSkip(id, number, e.getCode(), e.getMessage()));
            }
        }
        return new BulkResult(approved, skipped);
    }

    private void doApprove(PlayerRegistration r) {
        requirePending(r);
        List<Blocker> blockers = blockers(r, files.require(r.getPhotoAssetId(), FilePurpose.PLAYER_PHOTO));
        if (!blockers.isEmpty()) {
            Blocker b = blockers.get(0);
            throw ApiException.unprocessable(b.code(), b.message());
        }
        UUID playerId = players.upsertProfile(r.getLinkedPlayerId(),
                new PlayerService.ProfileData(r.getFullName(), r.getDateOfBirth(), r.getGender(), r.getEmail(),
                        r.getPhone(), r.getAddress(), r.getHomeArea(), r.getExperience()),
                r.getPhotoAssetId());
        players.enrol(r.getTournamentId(), playerId, r.getId(), r.getSkillCategoryId());
        files.publish(r.getPhotoAssetId());
        r.approve(playerId, CurrentUser.requireId(), clock.instant());
        audit.record(AuditEvent.of("registration.player.approved", "PlayerRegistration", r.getId())
                .inTournament(r.getTournamentId())
                .withChange(Map.of("status", "PENDING"), Map.of("status", "APPROVED", "playerId", playerId.toString(),
                        "linked", r.getLinkedPlayerId() != null)));
    }

    @Transactional
    @PreAuthorize("hasAuthority('REGISTRATION_REVIEW')")
    public PlayerRegistrationDetail reject(UUID id, String reason, String message, Long version) {
        PlayerRegistration r = load(id);
        checkVersion(r, version);
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "Choose or enter a reason.");
        }
        r.reject(reason.trim(), blank(message), CurrentUser.requireId(), clock.instant());
        audit.record(AuditEvent.of("registration.player.rejected", "PlayerRegistration", r.getId())
                .inTournament(r.getTournamentId())
                .withChange(Map.of("status", "PENDING"), Map.of("status", "REJECTED"))
                .withReason(reason.trim()));
        return respond(r);
    }

    @Transactional
    @PreAuthorize("hasAuthority('REGISTRATION_REVIEW')")
    public PlayerRegistrationDetail addNote(UUID id, String body) {
        PlayerRegistration r = load(id);
        addNote(notes, RegistrationKind.PLAYER, r.getId(), body, clock.instant());
        return respond(r);
    }

    static void addNote(RegistrationNoteRepository notes, RegistrationKind kind, UUID id, String body, Instant now) {
        if (body == null || body.isBlank()) {
            throw ApiException.badRequest("NOTE_EMPTY", "Write a note first.");
        }
        String text = body.trim();
        if (text.length() > 1000) {
            throw ApiException.badRequest("NOTE_TOO_LONG", "Keep notes under 1000 characters.");
        }
        CurrentUser.Actor me = CurrentUser.get().orElseThrow();
        notes.save(new RegistrationNote(kind, id, me.id(), me.name(), text, now));
    }

    // ---- Helpers ------------------------------------------------------------------------

    private List<Blocker> blockers(PlayerRegistration r, FileAsset photo) {
        List<Blocker> out = new ArrayList<>();
        if (r.getStatus() != RegistrationStatus.PENDING) {
            return out;
        }
        if (photo.getVerificationStatus() != VerificationStatus.VERIFIED) {
            out.add(new Blocker("PHOTO_NOT_VERIFIED", photo.getVerificationStatus() == VerificationStatus.REJECTED
                    ? "The photo was rejected. Ask the player for a new one or reject the registration."
                    : "Check and verify the photo before approving."));
        }
        if (duplicates.countByRegistrationIdAndResolution(r.getId(), DuplicateResolution.OPEN) > 0) {
            out.add(new Blocker("DUPLICATE_UNRESOLVED", "Resolve the possible duplicates before approving."));
        }
        TournamentSettings s = tournaments.settingsFor(r.getTournamentId());
        if (s.isCategoryRequired() && r.getSkillCategoryId() == null) {
            out.add(new Blocker("CATEGORY_REQUIRED", "Assign a category before approving."));
        }
        if (r.getLinkedPlayerId() != null && players.isEnrolled(r.getTournamentId(), r.getLinkedPlayerId())) {
            out.add(new Blocker("ALREADY_IN_TOURNAMENT", "The linked player is already approved for this edition."));
        }
        return out;
    }

    private PlayerRegistrationDetail respond(PlayerRegistration r) {
        registrations.flush();
        return detail(r);
    }

    private PlayerRegistrationDetail detail(PlayerRegistration r) {
        Map<UUID, String> cats = categories.namesById(r.getTournamentId());
        FileAssetView photo = files.get(r.getPhotoAssetId());
        List<DuplicateView> dupes = duplicates.findByRegistrationIdOrderByCreatedAtAsc(r.getId()).stream()
                .map(d -> new DuplicateView(d.getId(), d.getMatchedOn(), d.getResolution(), d.getResolvedAt(),
                        d.getCandidateRegistrationId() == null ? null
                                : registrations.findById(d.getCandidateRegistrationId()).map(c -> new CandidateRegistration(
                                        c.getId(), c.getRegistrationNumber(), c.getFullName(), c.getEmail(), c.getPhone(),
                                        c.getDateOfBirth(), c.getStatus(), c.getSubmittedAt())).orElse(null),
                        players.summary(d.getCandidatePlayerId()).orElse(null)))
                .toList();
        List<NoteView> noteViews = notes(notes, RegistrationKind.PLAYER, r.getId());
        List<Blocker> blockers = r.getStatus() == RegistrationStatus.PENDING
                ? blockers(r, files.require(r.getPhotoAssetId(), FilePurpose.PLAYER_PHOTO))
                : List.of();
        return new PlayerRegistrationDetail(r.getId(), r.getRegistrationNumber(), r.getStatus(), r.getFullName(),
                r.getDateOfBirth(), r.getGender(), r.getEmail(), r.getPhone(), r.getAddress(), r.getHomeArea(),
                r.getExperience(), r.getEmergencyName(), r.getEmergencyPhone(), r.getSkillCategoryId(),
                cats.get(r.getSkillCategoryId()), photo, r.isDuplicateFlag(), dupes,
                players.summary(r.getLinkedPlayerId()).orElse(null), r.getPlayerId(), r.getSubmittedAt(),
                r.getReviewedAt(), r.getRejectionReason(), r.getRejectionMessage(), noteViews, blockers, r.getVersion());
    }

    static List<NoteView> notes(RegistrationNoteRepository repo, RegistrationKind kind, UUID id) {
        return repo.findByRegistrationKindAndRegistrationIdOrderByCreatedAtAsc(kind, id).stream()
                .map(n -> new NoteView(n.getId(), n.getAuthorName(), n.getBody(), n.getCreatedAt()))
                .toList();
    }

    private PlayerRegistration load(UUID id) {
        UUID t = tournaments.currentTournamentId();
        return registrations.findById(id).filter(r -> r.getTournamentId().equals(t))
                .orElseThrow(() -> ApiException.notFound("Registration"));
    }

    private static void requirePending(PlayerRegistration r) {
        if (r.getStatus() != RegistrationStatus.PENDING) {
            throw ApiException.conflict("REGISTRATION_NOT_PENDING",
                    "This registration has already been " + r.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
    }

    private static void checkVersion(PlayerRegistration r, Long expected) {
        if (expected != null && !Objects.equals(r.getVersion(), expected)) {
            throw ApiException.conflict("STALE_DATA", "Someone else changed this registration. Reload and try again.");
        }
    }

    private static List<Object> identity(PlayerRegistration r) {
        return java.util.Arrays.asList(r.getFullName(), r.getEmail(), r.getPhone(), r.getDateOfBirth());
    }

    /** Audit snapshot — contact details are deliberately left out of the log. */
    private static Map<String, Object> snapshot(PlayerRegistration r) {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", r.getFullName());
        m.put("categoryId", r.getSkillCategoryId());
        m.put("homeArea", r.getHomeArea());
        return m;
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
