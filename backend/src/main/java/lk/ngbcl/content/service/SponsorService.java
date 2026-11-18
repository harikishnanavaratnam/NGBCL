package lk.ngbcl.content.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.content.domain.Sponsor;
import lk.ngbcl.content.domain.SponsorRepository;
import lk.ngbcl.content.domain.SponsorTier;
import lk.ngbcl.files.domain.FilePurpose;
import lk.ngbcl.files.service.FileService;
import lk.ngbcl.files.service.UploadContext;
import lk.ngbcl.shared.domain.Ids;
import lk.ngbcl.shared.site.SiteRevalidator;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.FieldErrors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sponsors (CNT-005..007): title lockup, sponsor wall, auction ticker. */
@Service
public class SponsorService {

    private final SponsorRepository repository;
    private final FileService files;
    private final UploadContext tournament;
    private final AuditService audit;
    private final SiteRevalidator site;

    public SponsorService(SponsorRepository repository, FileService files, UploadContext tournament, AuditService audit,
            SiteRevalidator site) {
        this.repository = repository;
        this.files = files;
        this.tournament = tournament;
        this.audit = audit;
        this.site = site;
    }

    public record SponsorView(UUID id, String name, SponsorTier tier, UUID logoAssetId, String logoUrl,
            String websiteUrl, String awardName, boolean showOnWall, boolean showOnTicker, boolean active,
            int displayOrder, Long version) {
    }

    public record SponsorInput(String name, SponsorTier tier, UUID logoAssetId, String websiteUrl, String awardName,
            boolean showOnWall, boolean showOnTicker, boolean active) {
    }

    public record PublicSponsor(String name, SponsorTier tier, String logoUrl, String websiteUrl, String awardName,
            boolean showOnWall, boolean showOnTicker) {
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<SponsorView> list() {
        return sorted(repository.findByTournamentIdOrderByDisplayOrderAscNameAsc(tournament.currentTournamentId()))
                .stream().map(this::view).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public SponsorView create(SponsorInput in) {
        UUID t = tournament.currentTournamentId();
        Sponsor s = new Sponsor(t, repository.maxOrder(t) + 1);
        apply(s, in);
        repository.save(s);
        audit.record(AuditEvent.of("sponsor.created", "Sponsor", s.getId()).inTournament(t)
                .withChange(null, Map.of("name", s.getName(), "tier", s.getTier().name())));
        site.touch(SiteRevalidator.SPONSORS);
        repository.flush();
        return view(s);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public SponsorView update(UUID id, SponsorInput in, Long version) {
        Sponsor s = load(id);
        if (version != null && !Objects.equals(s.getVersion(), version)) {
            throw ApiException.conflict("STALE_DATA", "Someone else changed this sponsor. Reload and try again.");
        }
        Map<String, Object> before = snapshot(s);
        UUID oldLogo = s.getLogoAssetId();
        apply(s, in);
        if (oldLogo != null && !oldLogo.equals(s.getLogoAssetId())) {
            files.unpublish(oldLogo);
        }
        audit.record(AuditEvent.of("sponsor.updated", "Sponsor", s.getId()).inTournament(s.getTournamentId())
                .withChange(before, snapshot(s)));
        site.touch(SiteRevalidator.SPONSORS);
        repository.flush();
        return view(s);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<SponsorView> reorder(List<UUID> ids) {
        UUID t = tournament.currentTournamentId();
        List<Sponsor> all = repository.findByTournamentIdOrderByDisplayOrderAscNameAsc(t);
        Map<UUID, Sponsor> byId = new HashMap<>();
        all.forEach(s -> byId.put(s.getId(), s));
        if (ids.size() != all.size() || !byId.keySet().equals(new HashSet<>(ids))) {
            throw ApiException.badRequest("ORDER_INCOMPLETE", "Send every sponsor exactly once.");
        }
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).moveTo(i + 1);
        }
        audit.record(AuditEvent.of("sponsor.reordered", "Sponsor", null).inTournament(t));
        site.touch(SiteRevalidator.SPONSORS);
        repository.flush();
        return list();
    }

    private void apply(Sponsor s, SponsorInput in) {
        FieldErrors e = new FieldErrors();
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isEmpty() || name.length() > 120) {
            e.add("name", "Enter the sponsor name.");
        }
        if (in.tier() == null) {
            e.add("tier", "Choose a tier.");
        }
        String url = in.websiteUrl() == null || in.websiteUrl().isBlank() ? null : in.websiteUrl().trim();
        if (url != null && !url.matches("^https?://[^\\s]{3,290}$")) {
            e.add("websiteUrl", "Enter a full link starting with https://");
        }
        if (in.tier() == SponsorTier.TITLE && in.active()
                && repository.countOtherActiveTitle(s.getTournamentId(), s.isNew() ? Ids.newId() : s.getId()) > 0) {
            e.add("tier", "There is already an active title sponsor. Deactivate it first.");
        }
        e.throwIfAny();
        UUID logo = in.logoAssetId();
        if (logo != null && !logo.equals(s.getLogoAssetId())) {
            files.publishStaffImage(logo, FilePurpose.SPONSOR_LOGO);
        }
        String award = in.awardName() == null || in.awardName().isBlank() ? null : in.awardName().trim();
        s.edit(name, in.tier(), logo, url, award, in.showOnWall(), in.showOnTicker(), in.active());
    }

    private Sponsor load(UUID id) {
        return repository.findById(id)
                .filter(s -> s.getTournamentId().equals(tournament.currentTournamentId()))
                .orElseThrow(() -> ApiException.notFound("Sponsor"));
    }

    private static List<Sponsor> sorted(List<Sponsor> list) {
        List<Sponsor> out = new ArrayList<>(list);
        out.sort(Comparator.comparing(Sponsor::getTier).thenComparingInt(Sponsor::getDisplayOrder)
                .thenComparing(Sponsor::getName));
        return out;
    }

    private SponsorView view(Sponsor s) {
        return new SponsorView(s.getId(), s.getName(), s.getTier(), s.getLogoAssetId(),
                files.publicUrl(s.getLogoAssetId()).orElse(null), s.getWebsiteUrl(), s.getAwardName(), s.isShowOnWall(),
                s.isShowOnTicker(), s.isActive(), s.getDisplayOrder(), s.getVersion());
    }

    private static Map<String, Object> snapshot(Sponsor s) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", s.getName());
        m.put("tier", s.getTier() == null ? null : s.getTier().name());
        m.put("active", s.isActive());
        return m;
    }

    @Transactional(readOnly = true)
    public List<PublicSponsor> publicList() {
        return sorted(repository.findByTournamentIdOrderByDisplayOrderAscNameAsc(tournament.currentTournamentId()))
                .stream()
                .filter(Sponsor::isActive)
                .map(s -> new PublicSponsor(s.getName(), s.getTier(), files.publicUrl(s.getLogoAssetId()).orElse(null),
                        s.getWebsiteUrl(), s.getAwardName(), s.isShowOnWall(), s.isShowOnTicker()))
                .toList();
    }
}
