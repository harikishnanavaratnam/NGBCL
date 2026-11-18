package lk.ngbcl.registration.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.files.domain.FileAsset;
import lk.ngbcl.files.domain.FilePurpose;
import lk.ngbcl.files.service.FileService;
import lk.ngbcl.player.category.CategoryService;
import lk.ngbcl.registration.domain.PlayerRegistration;
import lk.ngbcl.registration.domain.PlayerRegistration.PlayerDetails;
import lk.ngbcl.registration.domain.PlayerRegistrationRepository;
import lk.ngbcl.registration.domain.RegistrationKind;
import lk.ngbcl.registration.domain.RegistrationSession;
import lk.ngbcl.registration.domain.RegistrationSessionRepository;
import lk.ngbcl.registration.domain.TeamRegistration;
import lk.ngbcl.registration.domain.TeamRegistrationRepository;
import lk.ngbcl.shared.domain.Ids;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.FieldErrors;
import lk.ngbcl.shared.web.RequestInfo;
import lk.ngbcl.team.service.TeamService;
import lk.ngbcl.tournament.domain.FieldRule;
import lk.ngbcl.tournament.domain.PlayerFieldRules;
import lk.ngbcl.tournament.domain.Tournament;
import lk.ngbcl.tournament.domain.TournamentSettings;
import lk.ngbcl.tournament.domain.WindowState;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public, anonymous registration (PLY-001..009, TEM-001..007). An applicant starts a short-lived
 * session, uploads their image against it, then submits once — with a Turnstile check. Every
 * rule (window, required fields, category, photo, declaration) is enforced here.
 */
@Service
public class PublicRegistrationService {

    static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}$");
    static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{9,15}$");
    static final Set<String> GENDERS = Set.of("MALE", "FEMALE");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TournamentService tournaments;
    private final RegistrationSessionRepository sessions;
    private final PlayerRegistrationRepository playerRegs;
    private final TeamRegistrationRepository teamRegs;
    private final FileService files;
    private final CategoryService categories;
    private final TeamService teams;
    private final ReferenceNumberService references;
    private final DuplicateDetector duplicates;
    private final TurnstileVerifier turnstile;
    private final RegistrationProperties props;
    private final RequestInfo requestInfo;
    private final AuditService audit;
    private final Clock clock;

    public PublicRegistrationService(TournamentService tournaments, RegistrationSessionRepository sessions,
            PlayerRegistrationRepository playerRegs, TeamRegistrationRepository teamRegs, FileService files,
            CategoryService categories, TeamService teams, ReferenceNumberService references,
            DuplicateDetector duplicates, TurnstileVerifier turnstile, RegistrationProperties props,
            RequestInfo requestInfo, AuditService audit, Clock clock) {
        this.tournaments = tournaments;
        this.sessions = sessions;
        this.playerRegs = playerRegs;
        this.teamRegs = teamRegs;
        this.files = files;
        this.categories = categories;
        this.teams = teams;
        this.references = references;
        this.duplicates = duplicates;
        this.turnstile = turnstile;
        this.props = props;
        this.requestInfo = requestInfo;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- Form configuration ------------------------------------------------------------

    public record PublicWindowInfo(WindowState state, Instant opensAt, Instant closesAt) {
    }

    public record PublicPhotoRules(long maxBytes, int minWidth, int minHeight, int ratioW, int ratioH) {
    }

    public record PublicPlayerForm(PublicWindowInfo window, PlayerFieldRules fields, boolean categoryRequired,
            List<CategoryService.PublicCategory> categories, PublicPhotoRules photo, String consentVersion,
            String turnstileSiteKey, Instant serverTime) {
    }

    public record PublicTeamForm(PublicWindowInfo window, Long entryFee, String currency, PublicPhotoRules logo,
            String consentVersion, String turnstileSiteKey, Instant serverTime) {
    }

    @Transactional
    public PublicPlayerForm playerForm() {
        Tournament t = tournaments.current();
        TournamentSettings s = tournaments.settingsFor(t.getId());
        Instant now = clock.instant();
        FileService.UploadLimits l = tournaments.limitsFor(FilePurpose.PLAYER_PHOTO);
        FilePurpose p = FilePurpose.PLAYER_PHOTO;
        return new PublicPlayerForm(
                new PublicWindowInfo(s.playerWindow(now, t.getStatus()), s.getPlayerRegOpensAt(), s.getPlayerRegClosesAt()),
                s.getPlayerFields(), s.isCategoryRequired(), categories.listActivePublic(),
                new PublicPhotoRules(l.maxBytes(), l.minWidth(), l.minHeight(), p.ratioW, p.ratioH),
                props.consentVersion(), siteKey(), now);
    }

    @Transactional
    public PublicTeamForm teamForm() {
        Tournament t = tournaments.current();
        TournamentSettings s = tournaments.settingsFor(t.getId());
        Instant now = clock.instant();
        FileService.UploadLimits l = tournaments.limitsFor(FilePurpose.TEAM_LOGO);
        FilePurpose p = FilePurpose.TEAM_LOGO;
        return new PublicTeamForm(
                new PublicWindowInfo(s.teamWindow(now, t.getStatus()), s.getTeamRegOpensAt(), s.getTeamRegClosesAt()),
                s.getTeamEntryFee(), t.getCurrency(),
                new PublicPhotoRules(l.maxBytes(), l.minWidth(), l.minHeight(), p.ratioW, p.ratioH),
                props.consentVersion(), siteKey(), now);
    }

    private String siteKey() {
        return props.turnstileSiteKey() == null || props.turnstileSiteKey().isBlank() ? null : props.turnstileSiteKey();
    }

    // ---- Sessions & uploads ---------------------------------------------------------------

    public record SessionTicket(String token, Instant expiresAt) {
    }

    @Transactional
    public SessionTicket startSession(RegistrationKind kind) {
        requireOpen(kind);
        String ip = requestInfo.ipHash();
        Instant now = clock.instant();
        if (sessions.countByIpHashAndCreatedAtAfter(ip, now.minus(Duration.ofHours(1))) >= props.sessionsPerIpPerHour()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many registration attempts from your connection. Please try again later.");
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        RegistrationSession session = new RegistrationSession(kind, RequestInfo.sha256Hex(token), ip, now,
                now.plus(props.sessionTtl()));
        sessions.save(session);
        return new SessionTicket(token, session.getExpiresAt());
    }

    @Transactional
    public FileService.UploadTicket createUpload(String token, String filename, String contentType, long size) {
        RegistrationSession session = session(token);
        if (!session.canUpload()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_UPLOADS",
                    "Too many upload attempts. Reload the page to start again.");
        }
        session.countUpload();
        FilePurpose purpose = purposeFor(session.getKind());
        return files.createSessionUpload(tournaments.currentTournamentId(), purpose, filename, contentType, size,
                tournaments.limitsFor(purpose), session.getId());
    }

    public record PublicUploadResult(UUID id, int width, int height) {
    }

    /** Invalid files are recorded and the 422 explains why (so not rolled back). */
    @Transactional(noRollbackFor = ApiException.class)
    public PublicUploadResult completeUpload(String token, UUID assetId) {
        RegistrationSession session = session(token);
        FileAsset a = files.completeForSession(assetId, session.getId(), tournaments.limitsFor(purposeFor(session.getKind())));
        return new PublicUploadResult(a.getId(), a.getWidth(), a.getHeight());
    }

    private static FilePurpose purposeFor(RegistrationKind kind) {
        return kind == RegistrationKind.PLAYER ? FilePurpose.PLAYER_PHOTO : FilePurpose.TEAM_LOGO;
    }

    private RegistrationSession session(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new ApiException(HttpStatus.GONE, "REGISTRATION_SESSION_EXPIRED", "Your form session expired. Reload the page to continue.");
        }
        RegistrationSession s = sessions.findByTokenHash(RequestInfo.sha256Hex(token))
                .filter(x -> x.isUsable(clock.instant()))
                .orElseThrow(() -> new ApiException(HttpStatus.GONE, "REGISTRATION_SESSION_EXPIRED",
                        "Your form session expired. Reload the page to continue."));
        return s;
    }

    private Tournament requireOpen(RegistrationKind kind) {
        Tournament t = tournaments.current();
        TournamentSettings s = tournaments.settingsFor(t.getId());
        Instant now = clock.instant();
        WindowState w = kind == RegistrationKind.PLAYER ? s.playerWindow(now, t.getStatus()) : s.teamWindow(now, t.getStatus());
        if (w != WindowState.OPEN) {
            String who = kind == RegistrationKind.PLAYER ? "Player" : "Team";
            throw ApiException.conflict(w == WindowState.CLOSED ? "REGISTRATION_CLOSED" : "REGISTRATION_NOT_OPEN",
                    w == WindowState.CLOSED ? who + " registration has closed." : who + " registration isn't open yet.");
        }
        return t;
    }

    // ---- Player submission ------------------------------------------------------------------

    public record PlayerSubmission(String fullName, LocalDate dateOfBirth, String gender, String email, String phone,
            String address, String homeArea, String experience, String emergencyName, String emergencyPhone,
            UUID skillCategoryId, UUID photoAssetId, boolean jaffnaDeclared, boolean consent, String turnstileToken) {
    }

    public record SubmissionReceipt(String referenceNumber, Instant submittedAt) {
    }

    @Transactional
    public SubmissionReceipt submitPlayer(String token, PlayerSubmission in) {
        turnstile.verify(in.turnstileToken());
        RegistrationSession session = session(token);
        requireKind(session, RegistrationKind.PLAYER);
        Tournament t = requireOpen(RegistrationKind.PLAYER);
        TournamentSettings s = tournaments.settingsFor(t.getId());
        PlayerFieldRules rules = s.getPlayerFields();

        FieldErrors errors = new FieldErrors();
        String fullName = name(in.fullName(), "fullName", errors);
        String email = email(in.email(), "email", errors);
        String phone = phone(in.phone(), "phone", true, errors);
        LocalDate dob = collect(rules.dateOfBirth(), in.dateOfBirth(), "dateOfBirth", errors);
        if (dob != null) {
            LocalDate today = LocalDate.now(ZoneId.of(t.getTimezone()));
            if (!dob.isBefore(today.minusYears(8)) || dob.isBefore(today.minusYears(90))) {
                errors.add("dateOfBirth", "Enter a valid date of birth.");
            }
        }
        String gender = collect(rules.gender(), upper(in.gender()), "gender", errors);
        if (gender != null && !GENDERS.contains(gender)) {
            errors.add("gender", "Choose an option.");
        }
        String address = collect(rules.address(), text(in.address(), 300, "address", errors), "address", errors);
        String homeArea = collect(rules.homeArea(), text(in.homeArea(), 80, "homeArea", errors), "homeArea", errors);
        String experience = collect(rules.experience(), text(in.experience(), 300, "experience", errors), "experience", errors);
        String emergencyName = collect(rules.emergencyContact(), text(in.emergencyName(), 120, "emergencyName", errors),
                "emergencyName", errors);
        String emergencyPhone = null;
        if (rules.emergencyContact().collect()) {
            emergencyPhone = phone(in.emergencyPhone(), "emergencyPhone", rules.emergencyContact().required(), errors);
        }
        UUID category = in.skillCategoryId();
        if (category == null && s.isCategoryRequired()) {
            errors.add("skillCategoryId", "Choose your category.");
        }
        if (category != null) {
            try {
                categories.requireActive(t.getId(), category);
            } catch (ApiException e) {
                errors.add("skillCategoryId", e.getMessage());
            }
        }
        if (!in.jaffnaDeclared()) {
            errors.add("jaffnaDeclared", "Confirm that you are from the Jaffna district.");
        }
        if (!in.consent()) {
            errors.add("consent", "Agree to how we use your details to continue.");
        }
        if (in.photoAssetId() == null) {
            errors.add("photoAssetId", "Add your profile photo.");
        }
        errors.throwIfAny();
        FileAsset photo = files.requireForSession(in.photoAssetId(), FilePurpose.PLAYER_PHOTO, session.getId());

        Instant now = clock.instant();
        String number = references.next(t.getId(), RegistrationKind.PLAYER, year(t));
        PlayerRegistration r = new PlayerRegistration(t.getId(), number,
                new PlayerDetails(fullName, dob, gender, email, phone, address, homeArea, experience, emergencyName,
                        emergencyPhone, category),
                photo.getId(), props.consentVersion(), now, requestInfo.ipHash());
        playerRegs.save(r);
        playerRegs.flush();
        int dupes = duplicates.detect(r, s);
        session.markUsed(now);
        audit.record(AuditEvent.of("registration.player.submitted", "PlayerRegistration", r.getId())
                .inTournament(t.getId())
                .withChange(null, Map.of("reference", number, "possibleDuplicates", dupes)));
        return new SubmissionReceipt(number, now);
    }

    // ---- Team submission ------------------------------------------------------------------

    public record TeamSubmission(String teamName, String description, UUID logoAssetId, String contactName,
            String contactEmail, String contactPhone, boolean authorised, boolean feeAcknowledged, boolean consent,
            String turnstileToken) {
    }

    @Transactional
    public SubmissionReceipt submitTeam(String token, TeamSubmission in) {
        turnstile.verify(in.turnstileToken());
        RegistrationSession session = session(token);
        requireKind(session, RegistrationKind.TEAM);
        Tournament t = requireOpen(RegistrationKind.TEAM);

        FieldErrors errors = new FieldErrors();
        String teamName = in.teamName() == null ? "" : in.teamName().trim().replaceAll("\\s+", " ");
        if (teamName.length() < 3 || teamName.length() > 40) {
            errors.add("teamName", "Team names are 3–40 characters.");
        } else if (!nameAvailable(t.getId(), teamName)) {
            errors.add("teamName", "That team name is already taken.");
        }
        String description = text(in.description(), 300, "description", errors);
        String contactName = name(in.contactName(), "contactName", errors);
        String contactEmail = email(in.contactEmail(), "contactEmail", errors);
        String contactPhone = phone(in.contactPhone(), "contactPhone", true, errors);
        if (!in.authorised()) {
            errors.add("authorised", "Confirm you're authorised to register this team.");
        }
        if (!in.feeAcknowledged()) {
            errors.add("feeAcknowledged", "Confirm you've read the entry fee notice.");
        }
        if (!in.consent()) {
            errors.add("consent", "Agree to how we use your details to continue.");
        }
        if (in.logoAssetId() == null) {
            errors.add("logoAssetId", "Add your team logo.");
        }
        errors.throwIfAny();
        FileAsset logo = files.requireForSession(in.logoAssetId(), FilePurpose.TEAM_LOGO, session.getId());

        Instant now = clock.instant();
        String number = references.next(t.getId(), RegistrationKind.TEAM, year(t));
        TeamRegistration r = new TeamRegistration(t.getId(), number, teamName, description, logo.getId(), contactName,
                contactEmail, contactPhone, props.consentVersion(), now, requestInfo.ipHash());
        teamRegs.save(r);
        session.markUsed(now);
        audit.record(AuditEvent.of("registration.team.submitted", "TeamRegistration", r.getId())
                .inTournament(t.getId()).withChange(null, Map.of("reference", number, "teamName", teamName)));
        return new SubmissionReceipt(number, now);
    }

    /** TEM-011: unique among teams and pending applications. Answers only yes/no. */
    @Transactional(readOnly = true)
    public boolean nameAvailable(UUID tournamentId, String name) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.length() < 3) {
            return false;
        }
        return !teams.nameTaken(tournamentId, n, null) && teamRegs.countPendingWithName(tournamentId, n, Ids.newId()) == 0;
    }

    @Transactional(readOnly = true)
    public boolean nameAvailable(String name) {
        return nameAvailable(tournaments.currentTournamentId(), name);
    }

    // ---- Field helpers ----------------------------------------------------------------------

    private static void requireKind(RegistrationSession s, RegistrationKind kind) {
        if (s.getKind() != kind) {
            throw new ApiException(HttpStatus.GONE, "REGISTRATION_SESSION_EXPIRED", "Your form session expired. Reload the page to continue.");
        }
    }

    private static int year(Tournament t) {
        return t.getStartsOn() != null ? t.getStartsOn().getYear() : LocalDate.now(ZoneId.of(t.getTimezone())).getYear();
    }

    private static <T> T collect(FieldRule rule, T value, String field, FieldErrors errors) {
        if (!rule.collect()) {
            return null;
        }
        boolean empty = value == null || (value instanceof String str && str.isBlank());
        if (empty && rule.required() && !errors.has(field)) {
            errors.add(field, "This field is required.");
        }
        return empty ? null : value;
    }

    static String name(String v, String field, FieldErrors errors) {
        String n = v == null ? "" : v.trim().replaceAll("\\s+", " ");
        if (n.length() < 2 || n.length() > 120) {
            errors.add(field, "Enter a full name.");
        }
        return n;
    }

    static String email(String v, String field, FieldErrors errors) {
        String e = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        if (e.length() > 254 || !EMAIL.matcher(e).matches()) {
            errors.add(field, "Enter a valid email address.");
        }
        return e;
    }

    /** Keeps digits and a leading +, e.g. "077 123-4567" → "0771234567". */
    static String phone(String v, String field, boolean required, FieldErrors errors) {
        if (v == null || v.isBlank()) {
            if (required) {
                errors.add(field, "Enter a phone number.");
            }
            return null;
        }
        String trimmed = v.trim();
        String p = (trimmed.startsWith("+") ? "+" : "") + trimmed.replaceAll("[^0-9]", "");
        if (!PHONE.matcher(p).matches()) {
            errors.add(field, "Enter a valid phone number.");
        }
        return p;
    }

    private static String text(String v, int max, String field, FieldErrors errors) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String t = v.trim();
        if (t.length() > max) {
            errors.add(field, "Keep this under " + max + " characters.");
        }
        return t;
    }

    private static String upper(String v) {
        return v == null ? null : v.trim().toUpperCase(Locale.ROOT);
    }
}
