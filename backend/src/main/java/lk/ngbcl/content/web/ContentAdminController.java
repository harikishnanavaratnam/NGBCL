package lk.ngbcl.content.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.content.domain.AnnouncementStatus;
import lk.ngbcl.content.domain.CommitteeStyle;
import lk.ngbcl.content.domain.SponsorTier;
import lk.ngbcl.content.service.AnnouncementService;
import lk.ngbcl.content.service.AnnouncementService.AnnouncementInput;
import lk.ngbcl.content.service.AnnouncementService.AnnouncementView;
import lk.ngbcl.content.service.CommitteeService;
import lk.ngbcl.content.service.CommitteeService.GroupView;
import lk.ngbcl.content.service.CommitteeService.MemberInput;
import lk.ngbcl.content.service.SponsorService;
import lk.ngbcl.content.service.SponsorService.SponsorInput;
import lk.ngbcl.content.service.SponsorService.SponsorView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin › Content: announcements, sponsors, committee. All methods require CONTENT_MANAGE. */
@RestController
@RequestMapping("/api/admin/content")
public class ContentAdminController {

    private final AnnouncementService announcements;
    private final SponsorService sponsors;
    private final CommitteeService committee;

    public ContentAdminController(AnnouncementService announcements, SponsorService sponsors, CommitteeService committee) {
        this.announcements = announcements;
        this.sponsors = sponsors;
        this.committee = committee;
    }

    public record AnnouncementRequest(@Size(max = 160) String title, @Size(max = AnnouncementService.MAX_BODY) String body,
            UUID coverAssetId, boolean pinned, AnnouncementStatus status, Instant publishAt, Long version) {

        AnnouncementInput input() {
            return new AnnouncementInput(title, body, coverAssetId, pinned, status, publishAt);
        }
    }

    public record SponsorRequest(@Size(max = 120) String name, SponsorTier tier, UUID logoAssetId,
            @Size(max = 300) String websiteUrl, @Size(max = 160) String awardName, boolean showOnWall,
            boolean showOnTicker, boolean active, Long version) {

        SponsorInput input() {
            return new SponsorInput(name, tier, logoAssetId, websiteUrl, awardName, showOnWall, showOnTicker, active);
        }
    }

    public record OrderRequest(@NotEmpty List<UUID> ids) {
    }

    public record GroupRequest(@Size(max = 80) String name, CommitteeStyle style, Long version) {
    }

    public record MemberRequest(UUID groupId, @Size(max = 120) String fullName, @Size(max = 120) String role,
            @Size(max = 200) String bio, UUID photoAssetId, boolean visible, Long version) {

        MemberInput input() {
            return new MemberInput(groupId, fullName, role, bio, photoAssetId, visible);
        }
    }

    // ---- Announcements -------------------------------------------------------------------

    @GetMapping("/announcements")
    public List<AnnouncementView> announcements() {
        return announcements.list();
    }

    @PostMapping("/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementView createAnnouncement(@Valid @RequestBody AnnouncementRequest b) {
        return announcements.create(b.input());
    }

    @PatchMapping("/announcements/{id}")
    public AnnouncementView updateAnnouncement(@PathVariable UUID id, @Valid @RequestBody AnnouncementRequest b) {
        return announcements.update(id, b.input(), b.version());
    }

    // ---- Sponsors ------------------------------------------------------------------------

    @GetMapping("/sponsors")
    public List<SponsorView> sponsors() {
        return sponsors.list();
    }

    @PostMapping("/sponsors")
    @ResponseStatus(HttpStatus.CREATED)
    public SponsorView createSponsor(@Valid @RequestBody SponsorRequest b) {
        return sponsors.create(b.input());
    }

    @PatchMapping("/sponsors/{id}")
    public SponsorView updateSponsor(@PathVariable UUID id, @Valid @RequestBody SponsorRequest b) {
        return sponsors.update(id, b.input(), b.version());
    }

    @PutMapping("/sponsors/order")
    public List<SponsorView> reorderSponsors(@Valid @RequestBody OrderRequest b) {
        return sponsors.reorder(b.ids());
    }

    // ---- Committee -----------------------------------------------------------------------

    @GetMapping("/committee")
    public List<GroupView> committee() {
        return committee.list();
    }

    @PostMapping("/committee/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public List<GroupView> createGroup(@Valid @RequestBody GroupRequest b) {
        return committee.createGroup(b.name(), b.style());
    }

    @PatchMapping("/committee/groups/{id}")
    public List<GroupView> updateGroup(@PathVariable UUID id, @Valid @RequestBody GroupRequest b) {
        return committee.updateGroup(id, b.name(), b.style(), b.version());
    }

    @DeleteMapping("/committee/groups/{id}")
    public List<GroupView> deleteGroup(@PathVariable UUID id) {
        return committee.deleteGroup(id);
    }

    @PutMapping("/committee/groups/order")
    public List<GroupView> reorderGroups(@Valid @RequestBody OrderRequest b) {
        return committee.reorderGroups(b.ids());
    }

    @PutMapping("/committee/groups/{id}/members/order")
    public List<GroupView> reorderMembers(@PathVariable UUID id, @Valid @RequestBody OrderRequest b) {
        return committee.reorderMembers(id, b.ids());
    }

    @PostMapping("/committee/members")
    @ResponseStatus(HttpStatus.CREATED)
    public List<GroupView> createMember(@Valid @RequestBody MemberRequest b) {
        return committee.createMember(b.input());
    }

    @PatchMapping("/committee/members/{id}")
    public List<GroupView> updateMember(@PathVariable UUID id, @Valid @RequestBody MemberRequest b) {
        return committee.updateMember(id, b.input(), b.version());
    }

    @DeleteMapping("/committee/members/{id}")
    public List<GroupView> deleteMember(@PathVariable UUID id) {
        return committee.deleteMember(id);
    }
}
