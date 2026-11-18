package lk.ngbcl.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

/** Committee member — public name, role and optional photo/bio only. No contact details. */
@Entity
@Table(name = "committee_members")
public class CommitteeMember extends AuditedEntity {

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "role", nullable = false, length = 120)
    private String role;

    @Column(name = "bio", length = 200)
    private String bio;

    @Column(name = "photo_asset_id")
    private UUID photoAssetId;

    @Column(name = "visible", nullable = false)
    private boolean visible = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected CommitteeMember() {
    }

    public CommitteeMember(UUID groupId, int displayOrder) {
        this.groupId = groupId;
        this.displayOrder = displayOrder;
    }

    public void edit(UUID groupId, String fullName, String role, String bio, UUID photoAssetId, boolean visible) {
        this.groupId = groupId;
        this.fullName = fullName;
        this.role = role;
        this.bio = bio;
        this.photoAssetId = photoAssetId;
        this.visible = visible;
    }

    public void moveTo(int order) {
        this.displayOrder = order;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRole() {
        return role;
    }

    public String getBio() {
        return bio;
    }

    public UUID getPhotoAssetId() {
        return photoAssetId;
    }

    public boolean isVisible() {
        return visible;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
