package lk.ngbcl.player.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.files.service.FileService;
import lk.ngbcl.files.service.UploadContext;
import lk.ngbcl.player.category.CategoryService;
import lk.ngbcl.player.domain.Player;
import lk.ngbcl.player.domain.PlayerRepository;
import lk.ngbcl.player.domain.TournamentPlayer;
import lk.ngbcl.player.domain.TournamentPlayerRepository;
import lk.ngbcl.player.domain.TournamentPlayerStatus;
import lk.ngbcl.shared.domain.Slugs;
import lk.ngbcl.shared.site.SiteRevalidator;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Player profiles and the edition's player directory (SRS §6.2, PLY-024..028). */
@Service
public class PlayerService {

    private final PlayerRepository players;
    private final TournamentPlayerRepository entries;
    private final CategoryService categories;
    private final FileService files;
    private final UploadContext tournament;
    private final AuditService audit;
    private final EntityManager em;
    private final SiteRevalidator site;

    public PlayerService(PlayerRepository players, TournamentPlayerRepository entries, CategoryService categories,
            FileService files, UploadContext tournament, AuditService audit, EntityManager em, SiteRevalidator site) {
        this.site = site;
        this.players = players;
        this.entries = entries;
        this.categories = categories;
        this.files = files;
        this.tournament = tournament;
        this.audit = audit;
        this.em = em;
    }

    // ---- Used by the registration module (inside its transaction) --------------------

    public record ProfileData(String fullName, LocalDate dateOfBirth, String gender, String email, String phone,
            String address, String homeArea, String experience) {
    }

    /** A possible match for duplicate detection — internal only. */
    public record Candidate(UUID playerId, String fullName, String matchedOn) {
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(String email, String phone, String fullName, LocalDate dob, boolean byEmail,
            boolean byPhone, boolean byNameDob) {
        Map<UUID, Candidate> out = new java.util.LinkedHashMap<>();
        if (byEmail && email != null) {
            players.findByEmail(email.toLowerCase(Locale.ROOT)).forEach(p -> add(out, p, "email"));
        }
        if (byPhone && phone != null) {
            players.findByPhone(phone).forEach(p -> add(out, p, "phone"));
        }
        if (byNameDob && fullName != null && dob != null) {
            players.findByNameAndDob(fullName.trim().toLowerCase(Locale.ROOT), dob).forEach(p -> add(out, p, "name+dob"));
        }
        return new ArrayList<>(out.values());
    }

    private static void add(Map<UUID, Candidate> out, Player p, String on) {
        out.merge(p.getId(), new Candidate(p.getId(), p.getFullName(), on),
                (a, b) -> new Candidate(a.playerId(), a.fullName(), a.matchedOn() + "," + b.matchedOn()));
    }

    /** Creates a profile, or updates the linked one with the newly approved details. Returns the player id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID upsertProfile(UUID linkedPlayerId, ProfileData d, UUID photoAssetId) {
        if (linkedPlayerId != null) {
            Player p = players.findById(linkedPlayerId).orElseThrow(() -> ApiException.notFound("Linked player"));
            p.update(d.fullName(), d.dateOfBirth(), d.gender(), d.email(), d.phone(), d.address(), d.homeArea(),
                    d.experience(), photoAssetId);
            return p.getId();
        }
        String slug = Slugs.unique(d.fullName(), players::existsBySlug);
        Player p = new Player(d.fullName(), d.dateOfBirth(), d.gender(), d.email(), d.phone(), d.address(),
                d.homeArea(), d.experience(), photoAssetId, slug);
        players.save(p);
        return p.getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enrol(UUID tournamentId, UUID playerId, UUID registrationId, UUID categoryId) {
        if (entries.findByTournamentIdAndPlayerId(tournamentId, playerId).isPresent()) {
            throw ApiException.conflict("ALREADY_IN_TOURNAMENT",
                    "This player is already approved for this edition. Reject this registration as a duplicate.");
        }
        TournamentPlayer tp = new TournamentPlayer(tournamentId, playerId, registrationId, categoryId);
        entries.save(tp);
        site.touch(SiteRevalidator.PLAYERS);
        return tp.getId();
    }

    @Transactional(readOnly = true)
    public boolean isEnrolled(UUID tournamentId, UUID playerId) {
        return entries.findByTournamentIdAndPlayerId(tournamentId, playerId).isPresent();
    }

    /** Staff-only summary of a profile, for duplicate review. */
    public record PlayerSummary(UUID id, String slug, String fullName, String email, String phone,
            LocalDate dateOfBirth, String homeArea, String photoUrl, boolean inThisEdition) {
    }

    @Transactional(readOnly = true)
    public Optional<PlayerSummary> summary(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        UUID t = tournament.currentTournamentId();
        return players.findById(playerId).map(p -> new PlayerSummary(p.getId(), p.getSlug(), p.getFullName(),
                p.getEmail(), p.getPhone(), p.getDateOfBirth(), p.getHomeArea(),
                files.publicUrl(p.getProfilePhotoAssetId()).orElse(null),
                entries.findByTournamentIdAndPlayerId(t, p.getId()).isPresent()));
    }

    // ---- Directory (staff) -----------------------------------------------------

    public record PlayerRow(UUID id, UUID playerId, String slug, String fullName, String email, String phone,
            String homeArea, String gender, LocalDate dateOfBirth, UUID categoryId, String categoryName,
            TournamentPlayerStatus status, String statusReason, String photoUrl, UUID registrationId, Long version) {
    }

    public record DirectoryFilter(String q, TournamentPlayerStatus status, UUID categoryId, int page, int size) {
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('REGISTRATION_VIEW', 'DIRECTORY_MANAGE')")
    public PageResponse<PlayerRow> directory(DirectoryFilter f) {
        UUID t = tournament.currentTournamentId();
        StringBuilder where = new StringBuilder(" from TournamentPlayer tp join Player p on p.id = tp.playerId where tp.tournamentId = :t");
        Map<String, Object> params = new HashMap<>();
        params.put("t", t);
        if (f.status() != null) {
            where.append(" and tp.status = :status");
            params.put("status", f.status());
        }
        if (f.categoryId() != null) {
            where.append(" and tp.skillCategoryId = :cat");
            params.put("cat", f.categoryId());
        }
        if (f.q() != null && !f.q().isBlank()) {
            where.append(" and (lower(p.fullName) like :q or lower(p.email) like :q or p.phone like :q)");
            params.put("q", "%" + f.q().trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%");
        }
        int size = Math.clamp(f.size(), 1, 100);
        int page = Math.max(0, f.page());
        TypedQuery<Long> count = em.createQuery("select count(tp)" + where, Long.class);
        TypedQuery<Object[]> rows = em.createQuery("select tp, p" + where + " order by lower(p.fullName), tp.id",
                Object[].class);
        params.forEach((k, v) -> {
            count.setParameter(k, v);
            rows.setParameter(k, v);
        });
        long total = count.getSingleResult();
        Map<UUID, String> cats = categories.namesById(t);
        List<PlayerRow> content = rows.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
                .map(r -> row((TournamentPlayer) r[0], (Player) r[1], cats))
                .toList();
        return new PageResponse<>(content, page, size, total, (int) Math.ceil(total / (double) size));
    }

    @Transactional
    @PreAuthorize("hasAuthority('DIRECTORY_MANAGE')")
    public PlayerRow changeStatus(UUID entryId, TournamentPlayerStatus status, String reason, Long version) {
        TournamentPlayer tp = loadEntry(entryId);
        checkVersion(tp.getVersion(), version);
        TournamentPlayerStatus before = tp.getStatus();
        tp.changeStatus(status, reason);
        audit.record(AuditEvent.of("player.status_changed", "TournamentPlayer", tp.getId())
                .inTournament(tp.getTournamentId())
                .withChange(Map.of("status", before.name()), Map.of("status", status.name()))
                .withReason(reason));
        return respond(tp);
    }

    @Transactional
    @PreAuthorize("hasAuthority('DIRECTORY_MANAGE')")
    public PlayerRow changeCategory(UUID entryId, UUID categoryId, Long version) {
        TournamentPlayer tp = loadEntry(entryId);
        checkVersion(tp.getVersion(), version);
        if (categoryId != null) {
            categories.requireActive(tp.getTournamentId(), categoryId);
        }
        UUID before = tp.getSkillCategoryId();
        tp.changeCategory(categoryId);
        Map<String, Object> b = new HashMap<>();
        b.put("categoryId", before);
        Map<String, Object> a = new HashMap<>();
        a.put("categoryId", categoryId);
        audit.record(AuditEvent.of("player.category_changed", "TournamentPlayer", tp.getId())
                .inTournament(tp.getTournamentId()).withChange(b, a));
        return respond(tp);
    }

    private PlayerRow respond(TournamentPlayer tp) {
        em.flush();
        site.touch(SiteRevalidator.PLAYERS);
        Player p = players.findById(tp.getPlayerId()).orElseThrow();
        return row(tp, p, categories.namesById(tp.getTournamentId()));
    }

    private TournamentPlayer loadEntry(UUID id) {
        return entries.findById(id)
                .filter(tp -> tp.getTournamentId().equals(tournament.currentTournamentId()))
                .orElseThrow(() -> ApiException.notFound("Player"));
    }

    private PlayerRow row(TournamentPlayer tp, Player p, Map<UUID, String> cats) {
        return new PlayerRow(tp.getId(), p.getId(), p.getSlug(), p.getFullName(), p.getEmail(), p.getPhone(),
                p.getHomeArea(), p.getGender(), p.getDateOfBirth(), tp.getSkillCategoryId(),
                cats.get(tp.getSkillCategoryId()), tp.getStatus(), tp.getStatusReason(),
                files.publicUrl(p.getProfilePhotoAssetId()).orElse(null), tp.getRegistrationId(), tp.getVersion());
    }

    static void checkVersion(Long actual, Long expected) {
        if (expected != null && !Objects.equals(actual, expected)) {
            throw ApiException.conflict("STALE_DATA", "Someone else changed this player. Reload and try again.");
        }
    }

    // ---- Public directory (PUB-004) — no contact details -------------------------------

    public record PublicPlayer(String slug, String fullName, String categoryName, String photoUrl) {
    }

    /** Public player page — public fields only (PUB-008). Team and price arrive with the auction (M4). */
    public record PublicPlayerDetail(String slug, String fullName, String categoryName, String photoUrl,
            String experience) {
    }

    @Transactional(readOnly = true)
    public PublicPlayerDetail publicDetail(String slug) {
        UUID t = tournament.currentTournamentId();
        Player p = players.findBySlug(slug == null ? "" : slug.toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Player"));
        TournamentPlayer tp = entries.findByTournamentIdAndPlayerId(t, p.getId())
                .filter(e -> e.getStatus() == TournamentPlayerStatus.APPROVED)
                .orElseThrow(() -> ApiException.notFound("Player"));
        return new PublicPlayerDetail(p.getSlug(), p.getFullName(), categories.namesById(t).get(tp.getSkillCategoryId()),
                files.publicUrl(p.getProfilePhotoAssetId()).orElse(null), p.getExperience());
    }

    @Transactional(readOnly = true)
    public List<PublicPlayer> publicList() {
        UUID t = tournament.currentTournamentId();
        Map<UUID, String> cats = categories.namesById(t);
        List<Object[]> rows = em.createQuery("""
                select tp, p from TournamentPlayer tp join Player p on p.id = tp.playerId
                where tp.tournamentId = :t and tp.status = :status order by lower(p.fullName)
                """, Object[].class)
                .setParameter("t", t)
                .setParameter("status", TournamentPlayerStatus.APPROVED)
                .getResultList();
        return rows.stream().map(r -> {
            TournamentPlayer tp = (TournamentPlayer) r[0];
            Player p = (Player) r[1];
            return new PublicPlayer(p.getSlug(), p.getFullName(), cats.get(tp.getSkillCategoryId()),
                    files.publicUrl(p.getProfilePhotoAssetId()).orElse(null));
        }).toList();
    }
}
