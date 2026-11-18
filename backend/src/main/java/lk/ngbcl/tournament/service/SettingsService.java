package lk.ngbcl.tournament.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
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
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.tournament.domain.Tournament;
import lk.ngbcl.tournament.domain.TournamentSettings;
import lk.ngbcl.tournament.service.SettingsDtos.Branding;
import lk.ngbcl.tournament.service.SettingsDtos.General;
import lk.ngbcl.tournament.service.SettingsDtos.PublicInfo;
import lk.ngbcl.tournament.service.SettingsDtos.Registration;
import lk.ngbcl.tournament.service.SettingsDtos.SettingsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin settings: every change is audited with before/after (brief: "saved to the audit log"). */
@Service
public class SettingsService {

    private final TournamentService tournaments;
    private final FileService files;
    private final AuditService audit;
    private final Clock clock;
    private final EntityManager em;

    public SettingsService(TournamentService tournaments, FileService files, AuditService audit, Clock clock,
            EntityManager em) {
        this.tournaments = tournaments;
        this.files = files;
        this.audit = audit;
        this.clock = clock;
        this.em = em;
    }

    /** Flush so @Version is bumped before we answer: the client sends these versions on its next save. */
    private SettingsResponse respond(Tournament t, TournamentSettings s) {
        em.flush();
        return toResponse(t, s);
    }

    @Transactional
    @PreAuthorize("hasAuthority('TOURNAMENT_VIEW')")
    public SettingsResponse get() {
        Tournament t = tournaments.current();
        return toResponse(t, tournaments.settingsFor(t.getId()));
    }

    @Transactional
    @PreAuthorize("hasAuthority('TOURNAMENT_MANAGE')")
    public SettingsResponse updateGeneral(SettingsDtos.UpdateGeneralRequest r) {
        Tournament t = tournaments.current();
        TournamentSettings s = tournaments.settingsFor(t.getId());
        requireVersion(t.getVersion(), r.tournamentVersion());
        requireVersion(s.getVersion(), r.settingsVersion());
        Map<String, Object> before = generalSnapshot(t, s);
        t.updateDetails(r.name(), r.shortName(), r.tagline(), r.description(), r.organiser(), r.presenterLine(),
                r.startsOn(), r.endsOn(), r.venue(), r.city(), r.courts());
        var p = r.publicInfo();
        s.updatePublicInfo(p.facebookUrl(), p.contactEmail(), p.contactPhone(), p.prizeFirst(), p.prizeSecond(),
                p.prizeThird(), p.teamEntryFee(), p.showPlayerPrices(), p.showTeamBudgets(), p.phaseOverride());
        record("settings.general_updated", t, before, generalSnapshot(t, s), null);
        return respond(t, s);
    }

    @Transactional
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public SettingsResponse updateRegistration(SettingsDtos.UpdateRegistrationRequest r) {
        Tournament t = tournaments.current();
        TournamentSettings s = tournaments.settingsFor(t.getId());
        requireVersion(s.getVersion(), r.settingsVersion());
        Map<String, Object> before = registrationSnapshot(s);
        s.updateRegistration(r.playerOpensAt(), r.playerClosesAt(), r.teamOpensAt(), r.teamClosesAt(),
                r.categoryRequired(), r.playerFields(), r.dupMatchEmail(), r.dupMatchPhone(), r.dupMatchNameDob(),
                r.maxPhotoBytes(), r.minPhotoWidth(), r.minPhotoHeight(), r.notifyOnRegistration());
        record("settings.registration_updated", t, before, registrationSnapshot(s), null);
        return respond(t, s);
    }

    /**
     * Attaches uploaded branding images. Admin uploads are trusted: they're verified and
     * published here, and the verification is audited with the admin as actor.
     */
    @Transactional
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public SettingsResponse updateBranding(SettingsDtos.UpdateBrandingRequest r) {
        Tournament t = tournaments.current();
        TournamentSettings s = tournaments.settingsFor(t.getId());
        requireVersion(s.getVersion(), r.settingsVersion());
        Map<String, Object> before = brandingSnapshot(s);
        UUID logo = attach(s.getLogoAssetId(), r.logoAssetId(), FilePurpose.TOURNAMENT_LOGO);
        UUID favicon = attach(s.getFaviconAssetId(), r.faviconAssetId(), FilePurpose.FAVICON);
        UUID organiser = attach(s.getOrganiserLogoAssetId(), r.organiserLogoAssetId(), FilePurpose.ORGANISER_LOGO);
        UUID poster = attach(s.getPosterAssetId(), r.posterAssetId(), FilePurpose.POSTER);
        s.updateBranding(logo, favicon, organiser, poster);
        record("settings.branding_updated", t, before, brandingSnapshot(s), null);
        return respond(t, s);
    }

    @Transactional
    @PreAuthorize("hasAuthority('TOURNAMENT_MANAGE')")
    public SettingsResponse changeStatus(SettingsDtos.ChangeStatusRequest r) {
        Tournament t = tournaments.current();
        String before = t.getStatus().name();
        t.changeStatus(r.status());
        record("tournament.status_changed", t, Map.of("status", before), Map.of("status", t.getStatus().name()),
                r.reason());
        return respond(t, tournaments.settingsFor(t.getId()));
    }

    private UUID attach(UUID current, UUID requested, FilePurpose purpose) {
        if (requested == null || requested.equals(current)) {
            return requested;
        }
        FileAsset asset = files.require(requested, purpose);
        if (asset.getVerificationStatus() != VerificationStatus.VERIFIED) {
            files.verify(asset.getId(), true, null);
        }
        files.publish(asset.getId());
        return requested;
    }

    SettingsResponse toResponse(Tournament t, TournamentSettings s) {
        Instant now = clock.instant();
        General general = new General(t.getId(), t.getSlug(), t.getName(), t.getShortName(), t.getEdition(),
                t.getTagline(), t.getDescription(), t.getOrganiser(), t.getPresenterLine(), t.getStartsOn(), t.getEndsOn(),
                t.getVenue(), t.getCity(), t.getCourts(), t.getStatus(), t.getTimezone(), t.getCurrency(),
                s.phase(now, t.getStatus()), versionOf(t.getVersion()));
        PublicInfo info = new PublicInfo(s.getFacebookUrl(), s.getContactEmail(), s.getContactPhone(), s.getPrizeFirst(),
                s.getPrizeSecond(), s.getPrizeThird(), s.getTeamEntryFee(), s.isShowPlayerPrices(), s.isShowTeamBudgets(),
                s.getPhaseOverride());
        Registration reg = new Registration(s.getPlayerRegOpensAt(), s.getPlayerRegClosesAt(),
                s.playerWindow(now, t.getStatus()), s.getTeamRegOpensAt(), s.getTeamRegClosesAt(),
                s.teamWindow(now, t.getStatus()), s.isCategoryRequired(), s.getPlayerFields(), s.isDupMatchEmail(),
                s.isDupMatchPhone(), s.isDupMatchNameDob(), s.getMaxPhotoBytes(), s.getMinPhotoWidth(),
                s.getMinPhotoHeight(), s.isNotifyOnRegistration());
        Branding branding = new Branding(asset(s.getLogoAssetId()), asset(s.getFaviconAssetId()),
                asset(s.getOrganiserLogoAssetId()), asset(s.getPosterAssetId()));
        return new SettingsResponse(general, info, reg, branding, versionOf(s.getVersion()), s.getUpdatedAt());
    }

    private FileAssetView asset(UUID id) {
        return id == null ? null : files.get(id);
    }

    private static long versionOf(Long v) {
        return v == null ? 0 : v;
    }

    private static void requireVersion(Long actual, Long expected) {
        if (expected != null && !Objects.equals(versionOf(actual), expected)) {
            throw ApiException.conflict("STALE_DATA", "Someone else saved these settings. Reload to see their changes.");
        }
    }

    private void record(String action, Tournament t, Map<String, Object> before, Map<String, Object> after, String reason) {
        audit.record(AuditEvent.of(action, "Tournament", t.getId()).inTournament(t.getId()).withChange(before, after)
                .withReason(reason));
    }

    private static Map<String, Object> generalSnapshot(Tournament t, TournamentSettings s) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", t.getName());
        m.put("shortName", t.getShortName());
        m.put("tagline", t.getTagline());
        m.put("organiser", t.getOrganiser());
        m.put("presenterLine", t.getPresenterLine());
        m.put("startsOn", str(t.getStartsOn()));
        m.put("endsOn", str(t.getEndsOn()));
        m.put("venue", t.getVenue());
        m.put("city", t.getCity());
        m.put("courts", t.getCourts());
        m.put("facebookUrl", s.getFacebookUrl());
        m.put("contactEmail", s.getContactEmail());
        m.put("contactPhone", s.getContactPhone());
        m.put("prizes", java.util.Arrays.asList(s.getPrizeFirst(), s.getPrizeSecond(), s.getPrizeThird()));
        m.put("teamEntryFee", s.getTeamEntryFee());
        m.put("showPlayerPrices", s.isShowPlayerPrices());
        m.put("showTeamBudgets", s.isShowTeamBudgets());
        m.put("phaseOverride", str(s.getPhaseOverride()));
        return m;
    }

    private static Map<String, Object> registrationSnapshot(TournamentSettings s) {
        Map<String, Object> m = new HashMap<>();
        m.put("playerOpensAt", str(s.getPlayerRegOpensAt()));
        m.put("playerClosesAt", str(s.getPlayerRegClosesAt()));
        m.put("teamOpensAt", str(s.getTeamRegOpensAt()));
        m.put("teamClosesAt", str(s.getTeamRegClosesAt()));
        m.put("categoryRequired", s.isCategoryRequired());
        m.put("playerFields", String.valueOf(s.getPlayerFields()));
        m.put("duplicateMatching", java.util.Arrays.asList(s.isDupMatchEmail(), s.isDupMatchPhone(), s.isDupMatchNameDob()));
        m.put("photoLimits", s.getMaxPhotoBytes() + "B " + s.getMinPhotoWidth() + "x" + s.getMinPhotoHeight());
        m.put("notifyOnRegistration", s.isNotifyOnRegistration());
        return m;
    }

    private static Map<String, Object> brandingSnapshot(TournamentSettings s) {
        Map<String, Object> m = new HashMap<>();
        m.put("logo", str(s.getLogoAssetId()));
        m.put("favicon", str(s.getFaviconAssetId()));
        m.put("organiserLogo", str(s.getOrganiserLogoAssetId()));
        m.put("poster", str(s.getPosterAssetId()));
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
