package lk.ngbcl.content.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.content.domain.CommitteeGroup;
import lk.ngbcl.content.domain.CommitteeGroupRepository;
import lk.ngbcl.content.domain.CommitteeMember;
import lk.ngbcl.content.domain.CommitteeMemberRepository;
import lk.ngbcl.content.domain.CommitteeStyle;
import lk.ngbcl.files.domain.FilePurpose;
import lk.ngbcl.files.service.FileService;
import lk.ngbcl.files.service.UploadContext;
import lk.ngbcl.shared.site.SiteRevalidator;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.FieldErrors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organising committee (CNT-008): groups of members with public name, role, optional photo/bio. */
@Service
public class CommitteeService {

    private final CommitteeGroupRepository groups;
    private final CommitteeMemberRepository members;
    private final FileService files;
    private final UploadContext tournament;
    private final AuditService audit;
    private final SiteRevalidator site;

    public CommitteeService(CommitteeGroupRepository groups, CommitteeMemberRepository members, FileService files,
            UploadContext tournament, AuditService audit, SiteRevalidator site) {
        this.groups = groups;
        this.members = members;
        this.files = files;
        this.tournament = tournament;
        this.audit = audit;
        this.site = site;
    }

    public record MemberView(UUID id, UUID groupId, String fullName, String role, String bio, UUID photoAssetId,
            String photoUrl, boolean visible, int displayOrder, Long version) {
    }

    public record GroupView(UUID id, String name, CommitteeStyle style, int displayOrder, List<MemberView> members,
            Long version) {
    }

    public record MemberInput(UUID groupId, String fullName, String role, String bio, UUID photoAssetId,
            boolean visible) {
    }

    public record PublicCommitteeMember(String fullName, String role, String bio, String photoUrl) {
    }

    public record PublicCommitteeGroup(String name, CommitteeStyle style, List<PublicCommitteeMember> members) {
    }

    // ---- Staff --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<GroupView> list() {
        List<CommitteeGroup> gs = groups.findByTournamentIdOrderByDisplayOrderAscNameAsc(tournament.currentTournamentId());
        Map<UUID, List<MemberView>> byGroup = new HashMap<>();
        if (!gs.isEmpty()) {
            members.findByGroupIdInOrderByDisplayOrderAscFullNameAsc(gs.stream().map(CommitteeGroup::getId).toList())
                    .forEach(m -> byGroup.computeIfAbsent(m.getGroupId(), k -> new ArrayList<>()).add(view(m)));
        }
        return gs.stream().map(g -> new GroupView(g.getId(), g.getName(), g.getStyle(), g.getDisplayOrder(),
                byGroup.getOrDefault(g.getId(), List.of()), g.getVersion())).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<GroupView> createGroup(String name, CommitteeStyle style) {
        UUID t = tournament.currentTournamentId();
        CommitteeGroup g = new CommitteeGroup(t, requireName(name), style == null ? CommitteeStyle.COMPACT : style,
                groups.maxOrder(t) + 1);
        groups.save(g);
        audit.record(AuditEvent.of("committee.group_created", "CommitteeGroup", g.getId()).inTournament(t)
                .withChange(null, Map.of("name", g.getName())));
        return changed();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<GroupView> updateGroup(UUID id, String name, CommitteeStyle style, Long version) {
        CommitteeGroup g = loadGroup(id);
        if (version != null && !Objects.equals(g.getVersion(), version)) {
            throw ApiException.conflict("STALE_DATA", "Someone else changed this group. Reload and try again.");
        }
        String before = g.getName();
        g.edit(requireName(name), style == null ? g.getStyle() : style);
        audit.record(AuditEvent.of("committee.group_updated", "CommitteeGroup", g.getId()).inTournament(g.getTournamentId())
                .withChange(Map.of("name", before), Map.of("name", g.getName())));
        return changed();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<GroupView> deleteGroup(UUID id) {
        CommitteeGroup g = loadGroup(id);
        if (members.countByGroupId(g.getId()) > 0) {
            throw ApiException.conflict("GROUP_NOT_EMPTY", "Move or remove the members in this group first.");
        }
        groups.delete(g);
        audit.record(AuditEvent.of("committee.group_deleted", "CommitteeGroup", g.getId()).inTournament(g.getTournamentId())
                .withChange(Map.of("name", g.getName()), null));
        return changed();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<GroupView> reorderGroups(List<UUID> ids) {
        List<CommitteeGroup> all = groups.findByTournamentIdOrderByDisplayOrderAscNameAsc(tournament.currentTournamentId());
        Map<UUID, CommitteeGroup> byId = new HashMap<>();
        all.forEach(g -> byId.put(g.getId(), g));
        if (ids.size() != all.size() || !byId.keySet().equals(new HashSet<>(ids))) {
            throw ApiException.badRequest("ORDER_INCOMPLETE", "Send every group exactly once.");
        }
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).moveTo(i + 1);
        }
        return changed();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<GroupView> reorderMembers(UUID groupId, List<UUID> ids) {
        CommitteeGroup g = loadGroup(groupId);
        List<CommitteeMember> all = members.findByGroupIdInOrderByDisplayOrderAscFullNameAsc(List.of(g.getId()));
        Map<UUID, CommitteeMember> byId = new HashMap<>();
        all.forEach(m -> byId.put(m.getId(), m));
        if (ids.size() != all.size() || !byId.keySet().equals(new HashSet<>(ids))) {
            throw ApiException.badRequest("ORDER_INCOMPLETE", "Send every member of the group exactly once.");
        }
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).moveTo(i + 1);
        }
        return changed();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<GroupView> createMember(MemberInput in) {
        CommitteeGroup g = loadGroup(in.groupId());
        CommitteeMember m = new CommitteeMember(g.getId(), members.maxOrder(g.getId()) + 1);
        apply(m, in, g);
        members.save(m);
        audit.record(AuditEvent.of("committee.member_created", "CommitteeMember", m.getId()).inTournament(g.getTournamentId())
                .withChange(null, Map.of("name", m.getFullName(), "role", m.getRole())));
        return changed();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<GroupView> updateMember(UUID id, MemberInput in, Long version) {
        CommitteeMember m = loadMember(id);
        if (version != null && !Objects.equals(m.getVersion(), version)) {
            throw ApiException.conflict("STALE_DATA", "Someone else changed this member. Reload and try again.");
        }
        CommitteeGroup g = loadGroup(in.groupId());
        UUID oldPhoto = m.getPhotoAssetId();
        Map<String, Object> before = Map.of("name", m.getFullName(), "role", m.getRole());
        if (!g.getId().equals(m.getGroupId())) {
            m.moveTo(members.maxOrder(g.getId()) + 1);
        }
        apply(m, in, g);
        if (oldPhoto != null && !oldPhoto.equals(m.getPhotoAssetId())) {
            files.unpublish(oldPhoto);
        }
        audit.record(AuditEvent.of("committee.member_updated", "CommitteeMember", m.getId()).inTournament(g.getTournamentId())
                .withChange(before, Map.of("name", m.getFullName(), "role", m.getRole())));
        return changed();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public List<GroupView> deleteMember(UUID id) {
        CommitteeMember m = loadMember(id);
        if (m.getPhotoAssetId() != null) {
            files.unpublish(m.getPhotoAssetId());
        }
        members.delete(m);
        audit.record(AuditEvent.of("committee.member_deleted", "CommitteeMember", m.getId())
                .inTournament(tournament.currentTournamentId()).withChange(Map.of("name", m.getFullName()), null));
        return changed();
    }

    private void apply(CommitteeMember m, MemberInput in, CommitteeGroup g) {
        FieldErrors e = new FieldErrors();
        String name = in.fullName() == null ? "" : in.fullName().trim();
        String role = in.role() == null ? "" : in.role().trim();
        if (name.length() < 2 || name.length() > 120) {
            e.add("fullName", "Enter the member's full name.");
        }
        if (role.isEmpty() || role.length() > 120) {
            e.add("role", "Enter the role as it should appear publicly.");
        }
        if (in.bio() != null && in.bio().trim().length() > 200) {
            e.add("bio", "Keep the bio to one line (200 characters).");
        }
        e.throwIfAny();
        UUID photo = in.photoAssetId();
        if (photo != null && !photo.equals(m.getPhotoAssetId())) {
            files.publishStaffImage(photo, FilePurpose.COMMITTEE_PHOTO);
        }
        String bio = in.bio() == null || in.bio().isBlank() ? null : in.bio().trim();
        m.edit(g.getId(), name, role, bio, photo, in.visible());
    }

    private static String requireName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 80) {
            throw ApiException.badRequest("GROUP_NAME_REQUIRED", "Enter a group name (up to 80 characters).");
        }
        return n;
    }

    private CommitteeGroup loadGroup(UUID id) {
        if (id == null) {
            throw ApiException.badRequest("GROUP_REQUIRED", "Choose a group.");
        }
        return groups.findById(id)
                .filter(g -> g.getTournamentId().equals(tournament.currentTournamentId()))
                .orElseThrow(() -> ApiException.notFound("Committee group"));
    }

    private CommitteeMember loadMember(UUID id) {
        CommitteeMember m = members.findById(id).orElseThrow(() -> ApiException.notFound("Committee member"));
        loadGroup(m.getGroupId()); // tournament check
        return m;
    }

    private List<GroupView> changed() {
        groups.flush();
        members.flush();
        site.touch(SiteRevalidator.COMMITTEE);
        return list();
    }

    private MemberView view(CommitteeMember m) {
        return new MemberView(m.getId(), m.getGroupId(), m.getFullName(), m.getRole(), m.getBio(), m.getPhotoAssetId(),
                files.publicUrl(m.getPhotoAssetId()).orElse(null), m.isVisible(), m.getDisplayOrder(), m.getVersion());
    }

    // ---- Public -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PublicCommitteeGroup> publicList() {
        List<CommitteeGroup> gs = groups.findByTournamentIdOrderByDisplayOrderAscNameAsc(tournament.currentTournamentId());
        if (gs.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PublicCommitteeMember>> byGroup = new HashMap<>();
        members.findByGroupIdInOrderByDisplayOrderAscFullNameAsc(gs.stream().map(CommitteeGroup::getId).toList())
                .stream().filter(CommitteeMember::isVisible)
                .forEach(m -> byGroup.computeIfAbsent(m.getGroupId(), k -> new ArrayList<>())
                        .add(new PublicCommitteeMember(m.getFullName(), m.getRole(), m.getBio(),
                                files.publicUrl(m.getPhotoAssetId()).orElse(null))));
        return gs.stream().filter(g -> byGroup.containsKey(g.getId()))
                .map(g -> new PublicCommitteeGroup(g.getName(), g.getStyle(), byGroup.get(g.getId()))).toList();
    }
}
