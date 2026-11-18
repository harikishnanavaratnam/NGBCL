package lk.ngbcl.files.service;

import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.files.domain.FilePurpose;
import lk.ngbcl.files.domain.UploadStatus;
import lk.ngbcl.files.domain.VerificationStatus;

/** Staff view of an asset. previewUrl is a short-lived private link; publicUrl only when published. */
public record FileAssetView(UUID id, FilePurpose purpose, UploadStatus uploadStatus,
        VerificationStatus verificationStatus, String contentType, Long size, Integer width, Integer height,
        String invalidReason, String rejectionReason, String previewUrl, String publicUrl, Instant publishedAt) {
}
