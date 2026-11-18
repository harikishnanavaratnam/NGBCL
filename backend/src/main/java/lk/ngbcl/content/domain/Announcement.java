package lk.ngbcl.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

/** News item from the organisers (CNT-001..004). Body is plain text with light markdown. */
@Entity
@Table(name = "announcements")
public class Announcement extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "cover_asset_id")
    private UUID coverAssetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnnouncementStatus status = AnnouncementStatus.DRAFT;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "publish_at")
    private Instant publishAt;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "author_name", length = 120)
    private String authorName;

    protected Announcement() {
    }

    public Announcement(UUID tournamentId, String slug, UUID authorId, String authorName) {
        this.tournamentId = tournamentId;
        this.slug = slug;
        this.authorId = authorId;
        this.authorName = authorName;
    }

    public void edit(String title, String body, UUID coverAssetId, boolean pinned) {
        this.title = title;
        this.body = body;
        this.coverAssetId = coverAssetId;
        this.pinned = pinned;
    }

    public void setStatus(AnnouncementStatus status, Instant publishAt) {
        this.status = status;
        this.publishAt = status == AnnouncementStatus.DRAFT ? null : publishAt;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public boolean isLive(Instant now) {
        return status == AnnouncementStatus.PUBLISHED && publishAt != null && !publishAt.isAfter(now);
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public String getTitle() {
        return title;
    }

    public String getSlug() {
        return slug;
    }

    public String getBody() {
        return body;
    }

    public UUID getCoverAssetId() {
        return coverAssetId;
    }

    public AnnouncementStatus getStatus() {
        return status;
    }

    public boolean isPinned() {
        return pinned;
    }

    public Instant getPublishAt() {
        return publishAt;
    }

    public String getAuthorName() {
        return authorName;
    }
}
