package lk.ngbcl.files.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;
import lk.ngbcl.shared.web.ApiException;

/** Metadata for one uploaded image (SRS §6.3 file_assets). The bytes live in R2. */
@Entity
@Table(name = "file_assets")
public class FileAsset extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private FilePurpose purpose;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "declared_type", nullable = false, length = 100)
    private String declaredType;

    @Column(name = "declared_size", nullable = false)
    private long declaredSize;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_extension", length = 10)
    private String fileExtension;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "object_key", nullable = false, length = 300)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    private UploadStatus uploadStatus = UploadStatus.PENDING;

    @Column(name = "invalid_reason")
    private String invalidReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "public_object_key", length = 300)
    private String publicObjectKey;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Set for anonymous registration uploads: only that session may complete or use the file. */
    @Column(name = "upload_session_id")
    private UUID uploadSessionId;

    protected FileAsset() {
    }

    public FileAsset(UUID tournamentId, FilePurpose purpose, String originalFilename, String declaredType,
            long declaredSize, UUID uploadedBy) {
        this.tournamentId = tournamentId;
        this.purpose = purpose;
        this.originalFilename = originalFilename;
        this.declaredType = declaredType;
        this.declaredSize = declaredSize;
        this.uploadedBy = uploadedBy;
        // Unique, server-generated key — never the user's filename (SRS §7.3).
        this.objectKey = "tournaments/" + tournamentId + "/" + purpose.folder + "/" + getId();
    }

    public void bindToSession(UUID sessionId) {
        this.uploadSessionId = sessionId;
    }

    public void markUploaded(String contentType, String extension, long size, int width, int height, String checksum) {
        requireStatus(UploadStatus.PENDING);
        this.contentType = contentType;
        this.fileExtension = extension;
        this.fileSize = size;
        this.width = width;
        this.height = height;
        this.checksum = checksum;
        this.uploadStatus = UploadStatus.UPLOADED;
    }

    public void markInvalid(String reason) {
        this.uploadStatus = UploadStatus.INVALID;
        this.invalidReason = reason;
    }

    public void markDeleted(Instant at) {
        this.uploadStatus = UploadStatus.DELETED;
        this.deletedAt = at;
        this.publicObjectKey = null;
        this.publishedAt = null;
    }

    public void verify(UUID by, Instant at) {
        requireStatus(UploadStatus.UPLOADED);
        this.verificationStatus = VerificationStatus.VERIFIED;
        this.verifiedBy = by;
        this.verifiedAt = at;
        this.rejectionReason = null;
    }

    public void reject(UUID by, Instant at, String reason) {
        this.verificationStatus = VerificationStatus.REJECTED;
        this.verifiedBy = by;
        this.verifiedAt = at;
        this.rejectionReason = reason;
        this.publicObjectKey = null;
        this.publishedAt = null;
    }

    public void markPublished(String publicKey, Instant at) {
        if (verificationStatus != VerificationStatus.VERIFIED) {
            throw ApiException.unprocessable("FILE_NOT_VERIFIED", "Only verified images can be published.");
        }
        this.publicObjectKey = publicKey;
        this.publishedAt = at;
    }

    public void markUnpublished() {
        this.publicObjectKey = null;
        this.publishedAt = null;
    }

    private void requireStatus(UploadStatus expected) {
        if (uploadStatus != expected) {
            throw ApiException.conflict("FILE_WRONG_STATE",
                    "This file is " + uploadStatus.name().toLowerCase() + " and can't be changed that way.");
        }
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public FilePurpose getPurpose() {
        return purpose;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getDeclaredType() {
        return declaredType;
    }

    public long getDeclaredSize() {
        return declaredSize;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public UploadStatus getUploadStatus() {
        return uploadStatus;
    }

    public String getInvalidReason() {
        return invalidReason;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getPublicObjectKey() {
        return publicObjectKey;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public UUID getUploadSessionId() {
        return uploadSessionId;
    }
}
