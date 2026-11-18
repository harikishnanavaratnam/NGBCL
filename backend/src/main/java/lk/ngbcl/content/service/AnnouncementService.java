package lk.ngbcl.content.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.content.domain.Announcement;
import lk.ngbcl.content.domain.AnnouncementRepository;
import lk.ngbcl.content.domain.AnnouncementStatus;
import lk.ngbcl.files.domain.FilePurpose;
import lk.ngbcl.files.service.FileService;
import lk.ngbcl.files.service.UploadContext;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.shared.domain.Slugs;
import lk.ngbcl.shared.site.SiteRevalidator;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.FieldErrors;
import lk.ngbcl.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Announcements / news (CNT-001..004). */
@Service
public class AnnouncementService {

    public static final int MAX_BODY = 20_000;

    private final AnnouncementRepository repository;
    private final FileService files;
    private final UploadContext tournament;
    private final AuditService audit;
    private final SiteRevalidator site;
    private final Clock clock;

    public AnnouncementService(AnnouncementRepository repository, FileService files, UploadContext tournament,
            AuditService audit, SiteRevalidator site, Clock clock) {
        this.repository = repository;
        this.files = files;
        this.tournament = tournament;
        this.audit = audit;
        this.site = site;
        this.clock = clock;
    }

    /** Staff view. displayStatus adds SCHEDULED for published items whose time is in the future. */
    public record AnnouncementView(UUID id, String title, String slug, String body, UUID coverAssetId, String coverUrl,
            AnnouncementStatus status, String displayStatus, boolean pinned, Instant publishAt, String authorName,
            Instant updatedAt, Long version) {
    }

    public record AnnouncementInput(String title, String body, UUID coverAssetId, boolean pinned,
            AnnouncementStatus status, Instant publishAt) {
    }

    public record PublicNewsItem(String slug, String title, String excerpt, String coverUrl, boolean pinned,
            Instant publishedAt) {
    }

    public record PublicNewsArticle(String slug, String title, String body, String coverUrl, Instant publishedAt) {
    }

    // ---- Staff --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<AnnouncementView> list() {
        return repository.findByTournamentIdOrderByUpdatedAtDesc(tournament.currentTournamentId()).stream()
                .map(this::view).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public AnnouncementView create(AnnouncementInput in) {
        UUID t = tournament.currentTournamentId();
        validate(in);
        CurrentUser.Actor me = CurrentUser.get().orElseThrow();
        String slug = Slugs.unique(in.title(), s -> repository.existsByTournamentIdAndSlug(t, s));
        Announcement a = new Announcement(t, slug, me.id(), me.name());
        apply(a, in);
        repository.save(a);
        audit.record(AuditEvent.of("announcement.created", "Announcement", a.getId()).inTournament(t)
                .withChange(null, Map.of("title", a.getTitle(), "status", a.getStatus().name())));
        site.touch(SiteRevalidator.NEWS);
        return respond(a);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public AnnouncementView update(UUID id, AnnouncementInput in, Long version) {
        Announcement a = load(id);
        if (version != null && !Objects.equals(a.getVersion(), version)) {
            throw ApiException.conflict("STALE_DATA", "Someone else changed this announcement. Reload and try again.");
        }
        validate(in);
        Map<String, Object> before = snapshot(a);
        boolean wasLive = a.isLive(clock.instant());
        UUID oldCover = a.getCoverAssetId();
        // Slug stays stable once an item has been public, so shared links keep working.
        if (!wasLive && a.getStatus() == AnnouncementStatus.DRAFT && !in.title().trim().equals(a.getTitle())) {
            a.setSlug(Slugs.unique(in.title(), s -> !s.equals(a.getSlug())
                    && repository.existsByTournamentIdAndSlug(a.getTournamentId(), s)));
        }
        apply(a, in);
        if (oldCover != null && !oldCover.equals(a.getCoverAssetId())) {
            files.unpublish(oldCover);
        }
        audit.record(AuditEvent.of("announcement.updated", "Announcement", a.getId()).inTournament(a.getTournamentId())
                .withChange(before, snapshot(a)));
        site.touch(SiteRevalidator.NEWS);
        return respond(a);
    }

    private void apply(Announcement a, AnnouncementInput in) {
        UUID cover = in.coverAssetId();
        if (cover != null && !cover.equals(a.getCoverAssetId())) {
            files.publishStaffImage(cover, FilePurpose.NEWS_COVER);
        }
        a.edit(in.title().trim(), in.body().strip(), cover, in.pinned());
        AnnouncementStatus status = in.status() == null ? AnnouncementStatus.DRAFT : in.status();
        Instant when = in.publishAt() != null ? in.publishAt() : (a.getPublishAt() != null ? a.getPublishAt() : clock.instant());
        a.setStatus(status, when);
    }

    private static void validate(AnnouncementInput in) {
        FieldErrors e = new FieldErrors();
        if (in.title() == null || in.title().isBlank() || in.title().trim().length() > 160) {
            e.add("title", "Enter a title (up to 160 characters).");
        }
        if (in.body() == null || in.body().isBlank()) {
            e.add("body", "Write the announcement.");
        } else if (in.body().length() > MAX_BODY) {
            e.add("body", "Keep it under 20,000 characters.");
        }
        e.throwIfAny();
    }

    private Announcement load(UUID id) {
        return repository.findById(id)
                .filter(a -> a.getTournamentId().equals(tournament.currentTournamentId()))
                .orElseThrow(() -> ApiException.notFound("Announcement"));
    }

    private AnnouncementView respond(Announcement a) {
        repository.flush();
        return view(a);
    }

    private AnnouncementView view(Announcement a) {
        Instant now = clock.instant();
        String display = a.getStatus() == AnnouncementStatus.PUBLISHED && a.getPublishAt() != null && a.getPublishAt().isAfter(now)
                ? "SCHEDULED" : a.getStatus().name();
        return new AnnouncementView(a.getId(), a.getTitle(), a.getSlug(), a.getBody(), a.getCoverAssetId(),
                files.publicUrl(a.getCoverAssetId()).orElse(null), a.getStatus(), display, a.isPinned(),
                a.getPublishAt(), a.getAuthorName(), a.getUpdatedAt(), a.getVersion());
    }

    private static Map<String, Object> snapshot(Announcement a) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", a.getTitle());
        m.put("status", a.getStatus() == null ? null : a.getStatus().name());
        m.put("pinned", a.isPinned());
        m.put("publishAt", a.getPublishAt() == null ? null : a.getPublishAt().toString());
        return m;
    }

    // ---- Public -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<PublicNewsItem> publicList(int page, int size) {
        var p = repository.findLive(tournament.currentTournamentId(), clock.instant(),
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 50)));
        return PageResponse.of(p, a -> new PublicNewsItem(a.getSlug(), a.getTitle(), excerpt(a.getBody()),
                files.publicUrl(a.getCoverAssetId()).orElse(null), a.isPinned(), a.getPublishAt()));
    }

    @Transactional(readOnly = true)
    public PublicNewsArticle publicArticle(String slug) {
        Instant now = clock.instant();
        return repository.findByTournamentIdAndSlug(tournament.currentTournamentId(), slug.toLowerCase(Locale.ROOT))
                .filter(a -> a.isLive(now))
                .map(a -> new PublicNewsArticle(a.getSlug(), a.getTitle(), a.getBody(),
                        files.publicUrl(a.getCoverAssetId()).orElse(null), a.getPublishAt()))
                .orElseThrow(() -> ApiException.notFound("Article"));
    }

    /** First ~200 characters of the text without markdown marks. */
    static String excerpt(String body) {
        String plain = body.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("[*_`#>]", "")
                .replaceAll("(?m)^\\s*[-•]\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.length() <= 200) {
            return plain;
        }
        int cut = plain.lastIndexOf(' ', 200);
        return plain.substring(0, cut > 120 ? cut : 200) + "…";
    }
}
