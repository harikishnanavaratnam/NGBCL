package lk.ngbcl.content.web;

import java.time.Duration;
import java.util.List;
import lk.ngbcl.content.service.AnnouncementService;
import lk.ngbcl.content.service.AnnouncementService.PublicNewsArticle;
import lk.ngbcl.content.service.AnnouncementService.PublicNewsItem;
import lk.ngbcl.content.service.CommitteeService;
import lk.ngbcl.content.service.CommitteeService.PublicCommitteeGroup;
import lk.ngbcl.content.service.SponsorService;
import lk.ngbcl.content.service.SponsorService.PublicSponsor;
import lk.ngbcl.shared.web.PageResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public website content. Public* DTOs only — no author ids, drafts or contact details. */
@RestController
public class PublicContentController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic();

    private final AnnouncementService announcements;
    private final SponsorService sponsors;
    private final CommitteeService committee;

    public PublicContentController(AnnouncementService announcements, SponsorService sponsors, CommitteeService committee) {
        this.announcements = announcements;
        this.sponsors = sponsors;
        this.committee = committee;
    }

    @GetMapping("/api/public/news")
    public ResponseEntity<PageResponse<PublicNewsItem>> news(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok().cacheControl(CACHE).body(announcements.publicList(page, size));
    }

    @GetMapping("/api/public/news/{slug}")
    public ResponseEntity<PublicNewsArticle> article(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CACHE).body(announcements.publicArticle(slug));
    }

    @GetMapping("/api/public/sponsors")
    public ResponseEntity<List<PublicSponsor>> sponsors() {
        return ResponseEntity.ok().cacheControl(CACHE).body(sponsors.publicList());
    }

    @GetMapping("/api/public/committee")
    public ResponseEntity<List<PublicCommitteeGroup>> committee() {
        return ResponseEntity.ok().cacheControl(CACHE).body(committee.publicList());
    }
}
