package lk.ngbcl.team.service;

import java.time.Clock;
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
import lk.ngbcl.files.service.FileService;
import lk.ngbcl.files.service.UploadContext;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.shared.domain.Ids;
import lk.ngbcl.shared.domain.Slugs;
import lk.ngbcl.shared.site.SiteRevalidator;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.team.domain.Team;
import lk.ngbcl.team.domain.TeamRepository;
import lk.ngbcl.team.domain.TeamSource;
import lk.ngbcl.team.domain.TeamStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Teams: approved applications and admin-created teams (TEM-008..013). */
@Service
public class TeamService {

    public static final int COLOR_SLOTS = 10;

    private final TeamRepository teams;
    private final FileService files;
    private final UploadContext tournament;
    private final AuditService audit;
    private final Clock clock;
    private final SiteRevalidator site;
    private final org.springframework.beans.factory.ObjectProvider<SquadSource> squads;

    public TeamService(TeamRepository teams, FileService files, UploadContext tournament, AuditService audit,
            Clock clock, SiteRevalidator site, org.springframework.beans.factory.ObjectProvider<SquadSource> squads) {
        this.site = site;
        this.squads = squads;
        this.teams = teams;
        this.files = files;
        this.tournament = tournament;
        this.audit = audit;
        this.clock = clock;
    }

    public record TeamView(UUID id, String name, String slug, String shortCode, Short colorSlot, String description,
            String logoUrl, UUID logoAssetId, String contactName, String contactEmail, String contactPhone,
            TeamSource creationSource, TeamStatus status, boolean feeReceived, java.time.Instant feeReceivedAt,
            UUID teamRegistrationId, Long version) {
    }

    public record PublicTeam(String slug, String name, String shortCode, Short colorSlot, String description,
            String logoUrl) {
    }

    /** Public team page. Squad, spend and standing arrive with the auction (M4) and competition (M5–M7). */
    public record PublicTeamDetail(String slug, String name, String shortCode, Short colorSlot, String description,
            String logoUrl, List<SquadSource.SquadMember> squad) {
    }

    /** Team as shown in the auction (console rail, public display). No contact details. */
    public record TeamCard(UUID id, String slug, String name, String shortCode, Short colorSlot, String logoUrl) {
    }

    @Transactional(readOnly = true)
    public List<TeamCard> activeTeams(UUID tournamentId) {
        return teams.findByTournamentIdOrderByNameAsc(tournamentId).stream()
                .filter(t -> t.getStatus() == TeamStatus.ACTIVE)
                .map(this::card).toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, TeamCard> cards(UUID tournamentId) {
        Map<UUID, TeamCard> out = new HashMap<>();
        teams.findByTournamentIdOrderByNameAsc(tournamentId).forEach(t -> out.put(t.getId(), card(t)));
        return out;
    }

    private TeamCard card(Team t) {
        return new TeamCard(t.getId(), t.getSlug(), t.getName(), t.getShortCode(), t.getColorSlot(),
                files.publicUrl(t.getLogoAssetId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public PublicTeamDetail publicDetail(String slug) {
        UUID tid = tournament.currentTournamentId();
        Team t = teams.findByTournamentIdOrderByNameAsc(tid).stream()
                .filter(x -> x.getSlug().equalsIgnoreCase(slug) && x.getStatus() == TeamStatus.ACTIVE)
                .findFirst().orElseThrow(() -> ApiException.notFound("Team"));
        return new PublicTeamDetail(t.getSlug(), t.getName(), t.getShortCode(), t.getColorSlot(), t.getDescription(),
                files.publicUrl(t.getLogoAssetId()).orElse(null),
                squads.getIfAvailable() == null ? List.of() : squads.getIfAvailable().squadOf(t.getId()));
    }

    public record TeamInput(String name, String shortCode, Short colorSlot, String description, UUID logoAssetId,
            String contactName, String contactEmail, String contactPhone) {
    }

    // ---- Queries ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public boolean nameTaken(UUID tournamentId, String name, UUID exclude) {
        return teams.countWithName(tournamentId, name.trim(), exclude == null ? Ids.newId() : exclude) > 0;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('REGISTRATION_VIEW', 'DIRECTORY_MANAGE')")
    public List<TeamView> list() {
        return teams.findByTournamentIdOrderByNameAsc(tournament.currentTournamentId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<PublicTeam> publicList() {
        return teams.findByTournamentIdOrderByNameAsc(tournament.currentTournamentId()).stream()
                .filter(t -> t.getStatus() == TeamStatus.ACTIVE)
                .map(t -> new PublicTeam(t.getSlug(), t.getName(), t.getShortCode(), t.getColorSlot(),
                        t.getDescription(), files.publicUrl(t.getLogoAssetId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public String suggestShortCode(UUID tournamentId, String name) {
        UUID none = Ids.newId();
        String base = baseCode(name);
        if (teams.countWithCode(tournamentId, base, none) == 0) {
            return base;
        }
        String stem = base.substring(0, 2);
        for (char c : "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray()) {
            String candidate = stem + c;
            if (teams.countWithCode(tournamentId, candidate, none) == 0) {
                return candidate;
            }
        }
        throw ApiException.conflict("SHORT_CODE_EXHAUSTED", "Choose a short code for this team.");
    }

    static String baseCode(String name) {
        String[] words = Slugs.of(name).toUpperCase(Locale.ROOT).split("-");
        StringBuilder code = new StringBuilder();
        if (words.length >= 2) {
            for (String w : words) {
                if (!w.isEmpty() && code.length() < 3) {
                    code.append(w.charAt(0));
                }
            }
        }
        if (code.length() < 2) {
            String joined = String.join("", words).replaceAll("[^A-Z0-9]", "");
            code = new StringBuilder(joined.substring(0, Math.min(3, joined.length())));
        }
        while (code.length() < 2) {
            code.append('X');
        }
        return code.toString();
    }

    private Short firstFreeColor(UUID tournamentId) {
        UUID none = Ids.newId();
        for (short s = 1; s <= COLOR_SLOTS; s++) {
            if (teams.countWithColor(tournamentId, s, none) == 0) {
                return s;
            }
        }
        return null;
    }

    // ---- Creation -------------------------------------------------------------------

    /** Called by the registration module when an application is approved. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID createFromRegistration(UUID tournamentId, UUID registrationId, TeamInput in) {
        Team t = build(tournamentId, registrationId, TeamSource.PUBLIC_REGISTRATION, in);
        audit.record(AuditEvent.of("team.created", "Team", t.getId()).inTournament(tournamentId)
                .withChange(null, Map.of("name", t.getName(), "source", t.getCreationSource().name())));
        return t.getId();
    }

    @Transactional
    @PreAuthorize("hasAuthority('DIRECTORY_MANAGE')")
    public TeamView create(TeamInput in) {
        UUID tid = tournament.currentTournamentId();
        Team t = build(tid, null, TeamSource.ADMIN, in);
        audit.record(AuditEvent.of("team.created", "Team", t.getId()).inTournament(tid)
                .withChange(null, Map.of("name", t.getName(), "source", t.getCreationSource().name())));
        teams.flush();
        return view(t);
    }

    private Team build(UUID tournamentId, UUID registrationId, TeamSource source, TeamInput in) {
        String name = requireName(in.name());
        if (nameTaken(tournamentId, name, null)) {
            throw ApiException.conflict("TEAM_NAME_TAKEN", "A team called \"" + name + "\" already exists.");
        }
        String code = in.shortCode() == null || in.shortCode().isBlank()
                ? suggestShortCode(tournamentId, name)
                : requireCode(tournamentId, in.shortCode(), null);
        Short color = in.colorSlot() == null ? firstFreeColor(tournamentId) : requireColor(tournamentId, in.colorSlot(), null);
        String slug = Slugs.unique(name, s -> teams.existsByTournamentIdAndSlug(tournamentId, s));
        Team t = new Team(tournamentId, registrationId, source, name, slug, code, color,
                CurrentUser.get().map(CurrentUser.Actor::id).orElse(null));
        t.edit(name, slug, code, color, in.description());
        t.setContact(in.contactName(), in.contactEmail(), in.contactPhone());
        if (in.logoAssetId() != null) {
            t.setLogo(publishLogo(in.logoAssetId()));
        }
        teams.save(t);
        site.touch(SiteRevalidator.TEAMS);
        return t;
    }

    // ---- Edits ------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('DIRECTORY_MANAGE')")
    public TeamView update(UUID id, TeamInput in, Long version) {
        Team t = load(id);
        checkVersion(t, version);
        Map<String, Object> before = snapshot(t);
        String name = requireName(in.name());
        if (nameTaken(t.getTournamentId(), name, t.getId())) {
            throw ApiException.conflict("TEAM_NAME_TAKEN", "A team called \"" + name + "\" already exists.");
        }
        String code = requireCode(t.getTournamentId(), in.shortCode(), t.getId());
        Short color = in.colorSlot() == null ? null : requireColor(t.getTournamentId(), in.colorSlot(), t.getId());
        String slug = name.equalsIgnoreCase(t.getName()) ? t.getSlug()
                : Slugs.unique(name, s -> !s.equals(t.getSlug()) && teams.existsByTournamentIdAndSlug(t.getTournamentId(), s));
        t.edit(name, slug, code, color, in.description());
        t.setContact(in.contactName(), in.contactEmail(), in.contactPhone());
        if (in.logoAssetId() != null && !in.logoAssetId().equals(t.getLogoAssetId())) {
            UUID old = t.getLogoAssetId();
            t.setLogo(publishLogo(in.logoAssetId()));
            if (old != null) {
                files.unpublish(old);
            }
        }
        audit.record(AuditEvent.of("team.updated", "Team", t.getId()).inTournament(t.getTournamentId())
                .withChange(before, snapshot(t)));
        return respond(t);
    }

    @Transactional
    @PreAuthorize("hasAuthority('DIRECTORY_MANAGE')")
    public TeamView setFeeReceived(UUID id, boolean received, Long version) {
        Team t = load(id);
        checkVersion(t, version);
        boolean before = t.isFeeReceived();
        t.setFeeReceived(received, CurrentUser.requireId(), clock.instant());
        audit.record(AuditEvent.of("team.fee_" + (received ? "received" : "cleared"), "Team", t.getId())
                .inTournament(t.getTournamentId())
                .withChange(Map.of("feeReceived", before), Map.of("feeReceived", received)));
        return respond(t);
    }

    @Transactional
    @PreAuthorize("hasAuthority('DIRECTORY_MANAGE')")
    public TeamView setStatus(UUID id, TeamStatus status, String reason, Long version) {
        Team t = load(id);
        checkVersion(t, version);
        if (status == TeamStatus.INACTIVE && (reason == null || reason.isBlank())) {
            throw ApiException.badRequest("REASON_REQUIRED", "Give a reason for deactivating the team.");
        }
        TeamStatus before = t.getStatus();
        t.setStatus(status);
        audit.record(AuditEvent.of("team.status_changed", "Team", t.getId()).inTournament(t.getTournamentId())
                .withChange(Map.of("status", before.name()), Map.of("status", status.name())).withReason(reason));
        return respond(t);
    }

    // ---- Helpers ------------------------------------------------------------------------

    private UUID publishLogo(UUID assetId) {
        // Staff choosing the logo themselves counts as checking it.
        return files.publishStaffImage(assetId, FilePurpose.TEAM_LOGO);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("TEAM_NAME_REQUIRED", "Enter the team name.");
        }
        String n = name.trim().replaceAll("\\s+", " ");
        if (n.length() < 3 || n.length() > 40) {
            throw ApiException.badRequest("TEAM_NAME_LENGTH", "Team names are 3–40 characters.");
        }
        return n;
    }

    private String requireCode(UUID tournamentId, String raw, UUID exclude) {
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("^[A-Z0-9]{2,3}$")) {
            throw ApiException.badRequest("SHORT_CODE_INVALID", "Short codes are 2–3 letters or digits, e.g. MSH.");
        }
        if (teams.countWithCode(tournamentId, code, exclude == null ? Ids.newId() : exclude) > 0) {
            throw ApiException.conflict("SHORT_CODE_TAKEN", "Another team already uses " + code + ".");
        }
        return code;
    }

    private Short requireColor(UUID tournamentId, Short slot, UUID exclude) {
        if (slot < 1 || slot > COLOR_SLOTS) {
            throw ApiException.badRequest("COLOR_INVALID", "Pick one of the ten team colours.");
        }
        if (teams.countWithColor(tournamentId, slot, exclude == null ? Ids.newId() : exclude) > 0) {
            throw ApiException.conflict("COLOR_TAKEN", "Another team already uses that colour.");
        }
        return slot;
    }

    private Team load(UUID id) {
        return teams.findById(id)
                .filter(t -> t.getTournamentId().equals(tournament.currentTournamentId()))
                .orElseThrow(() -> ApiException.notFound("Team"));
    }

    private static void checkVersion(Team t, Long expected) {
        if (expected != null && !Objects.equals(t.getVersion(), expected)) {
            throw ApiException.conflict("STALE_DATA", "Someone else changed this team. Reload and try again.");
        }
    }

    private TeamView respond(Team t) {
        teams.flush();
        site.touch(SiteRevalidator.TEAMS);
        return view(t);
    }

    private static Map<String, Object> snapshot(Team t) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", t.getName());
        m.put("shortCode", t.getShortCode());
        m.put("colorSlot", t.getColorSlot());
        m.put("logoAssetId", t.getLogoAssetId());
        return m;
    }

    private TeamView view(Team t) {
        return new TeamView(t.getId(), t.getName(), t.getSlug(), t.getShortCode(), t.getColorSlot(),
                t.getDescription(), files.publicUrl(t.getLogoAssetId()).orElse(null), t.getLogoAssetId(),
                t.getContactName(), t.getContactEmail(), t.getContactPhone(), t.getCreationSource(), t.getStatus(),
                t.isFeeReceived(), t.getFeeReceivedAt(), t.getTeamRegistrationId(), t.getVersion());
    }
}
