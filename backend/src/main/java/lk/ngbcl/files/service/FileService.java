package lk.ngbcl.files.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.files.domain.FileAsset;
import lk.ngbcl.files.domain.FileAssetRepository;
import lk.ngbcl.files.domain.FilePurpose;
import lk.ngbcl.files.domain.UploadStatus;
import lk.ngbcl.files.domain.VerificationStatus;
import lk.ngbcl.files.storage.ObjectStorage;
import lk.ngbcl.files.storage.StorageProperties;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.shared.domain.Ids;
import lk.ngbcl.shared.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upload pipeline (SRS §7.2):
 * 1. {@link #createUpload} — validate the declared file, issue a short-lived presigned PUT.
 * 2. The browser uploads straight to R2 (private bucket).
 * 3. {@link #complete} — read the object back, check real type / size / dimensions / ratio.
 * 4. {@link #verify} — a person confirms the image (required before public use).
 * 5. {@link #publish} — re-encoded copy to the public bucket.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;
    static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    static final Duration PREVIEW_TTL = Duration.ofMinutes(5);

    private final FileAssetRepository repository;
    private final ObjectStorage storage;
    private final StorageProperties props;
    private final ImageInspector inspector;
    private final AuditService audit;
    private final Clock clock;

    public FileService(FileAssetRepository repository, ObjectStorage storage, StorageProperties props,
            ImageInspector inspector, AuditService audit, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.props = props;
        this.inspector = inspector;
        this.audit = audit;
        this.clock = clock;
    }

    /** Per-upload limits; player photo limits come from tournament settings. */
    public record UploadLimits(long maxBytes, int minWidth, int minHeight) {

        public static UploadLimits defaultsFor(FilePurpose purpose) {
            return new UploadLimits(DEFAULT_MAX_BYTES, purpose.minWidth, purpose.minHeight);
        }
    }

    public record UploadTicket(UUID assetId, String uploadUrl, String method, Map<String, String> headers,
            Instant expiresAt, long maxBytes, int minWidth, int minHeight) {
    }

    @Transactional
    public UploadTicket createUpload(UUID tournamentId, FilePurpose purpose, String filename, String contentType,
            long size, UploadLimits limits) {
        UUID uploader = CurrentUser.get().map(CurrentUser.Actor::id).orElse(null);
        return issue(tournamentId, purpose, filename, contentType, size, limits, uploader, null);
    }

    /** Anonymous upload scoped to a registration session (SRS §7.2 short-lived upload token). */
    @Transactional
    public UploadTicket createSessionUpload(UUID tournamentId, FilePurpose purpose, String filename,
            String contentType, long size, UploadLimits limits, UUID sessionId) {
        return issue(tournamentId, purpose, filename, contentType, size, limits, null, sessionId);
    }

    private UploadTicket issue(UUID tournamentId, FilePurpose purpose, String filename, String contentType, long size,
            UploadLimits limits, UUID uploader, UUID sessionId) {
        String type = contentType == null ? "" : contentType.trim().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw ApiException.badRequest("FILE_TYPE_NOT_ALLOWED", "Use a JPEG, PNG or WebP image.");
        }
        if (size <= 0 || size > limits.maxBytes()) {
            throw ApiException.badRequest("FILE_TOO_LARGE",
                    "The image must be smaller than " + (limits.maxBytes() / (1024 * 1024)) + " MB.");
        }
        FileAsset asset = new FileAsset(tournamentId, purpose, sanitize(filename), type, size, uploader);
        asset.bindToSession(sessionId);
        repository.save(asset);
        ObjectStorage.PresignedUpload put = storage.presignPrivatePut(asset.getObjectKey(), type, size, props.presignTtl());
        return new UploadTicket(asset.getId(), put.url(), "PUT", put.headers(), put.expiresAt(), limits.maxBytes(),
                limits.minWidth(), limits.minHeight());
    }

    /**
     * Confirms an upload. Invalid files are deleted from storage, kept as INVALID records for the
     * audit trail, and the caller gets a 422 with the reason.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public FileAssetView complete(UUID assetId, UploadLimits limits) {
        FileAsset asset = load(assetId);
        requireUploader(asset);
        return view(doComplete(asset, limits));
    }

    /** Completes an anonymous upload; the asset must belong to this registration session. */
    @Transactional(noRollbackFor = ApiException.class)
    public FileAsset completeForSession(UUID assetId, UUID sessionId, UploadLimits limits) {
        FileAsset asset = loadForSession(assetId, sessionId);
        return doComplete(asset, limits);
    }

    /** An uploaded, valid asset of the given purpose that belongs to this registration session. */
    @Transactional(readOnly = true)
    public FileAsset requireForSession(UUID assetId, FilePurpose purpose, UUID sessionId) {
        FileAsset asset = loadForSession(assetId, sessionId);
        if (asset.getPurpose() != purpose) {
            throw ApiException.badRequest("FILE_WRONG_PURPOSE", "This image was uploaded for a different use.");
        }
        if (asset.getUploadStatus() != UploadStatus.UPLOADED) {
            throw ApiException.unprocessable("FILE_NOT_READY", "This image hasn't finished uploading.");
        }
        return asset;
    }

    private FileAsset loadForSession(UUID assetId, UUID sessionId) {
        FileAsset asset = repository.findById(assetId).orElseThrow(() -> ApiException.notFound("File"));
        if (sessionId == null || !sessionId.equals(asset.getUploadSessionId())) {
            // Same answer as "missing" so asset ids can't be probed.
            throw ApiException.notFound("File");
        }
        return asset;
    }

    private FileAsset doComplete(FileAsset asset, UploadLimits limits) {
        if (asset.getUploadStatus() == UploadStatus.UPLOADED) {
            return asset;
        }
        if (asset.getUploadStatus() != UploadStatus.PENDING) {
            throw ApiException.conflict("FILE_WRONG_STATE", "This upload can no longer be completed.");
        }
        ObjectStorage.ObjectInfo info = storage.headPrivate(asset.getObjectKey())
                .orElseThrow(() -> ApiException.conflict("FILE_NOT_UPLOADED", "The file hasn't finished uploading yet."));
        if (info.size() > limits.maxBytes()) {
            reject(asset, "FILE_TOO_LARGE", "The image is larger than allowed.");
        }
        byte[] bytes = storage.readPrivate(asset.getObjectKey(), limits.maxBytes() + 1);
        if (bytes.length > limits.maxBytes()) {
            reject(asset, "FILE_TOO_LARGE", "The image is larger than allowed.");
        }
        ImageInspector.Format format = inspector.sniff(bytes)
                .orElseGet(() -> reject(asset, "FILE_TYPE_NOT_ALLOWED", "This file isn't a JPEG, PNG or WebP image."));
        if (!format.contentType.equals(asset.getDeclaredType())) {
            reject(asset, "FILE_TYPE_MISMATCH", "The file content doesn't match its type.");
        }
        ImageInspector.Dimensions d = inspector.dimensions(bytes)
                .orElseGet(() -> reject(asset, "FILE_UNREADABLE", "The image couldn't be read. Try another file."));
        if (!inspector.withinSafeLimits(d)) {
            reject(asset, "IMAGE_TOO_BIG", "The image dimensions are too large.");
        }
        if (d.width() < limits.minWidth() || d.height() < limits.minHeight()) {
            reject(asset, "IMAGE_TOO_SMALL", "The image must be at least " + limits.minWidth() + " × "
                    + limits.minHeight() + " px (yours is " + d.width() + " × " + d.height() + ").");
        }
        if (!inspector.ratioMatches(asset.getPurpose(), d)) {
            FilePurpose p = asset.getPurpose();
            reject(asset, "IMAGE_WRONG_RATIO", "Crop the image to " + p.ratioW + ":" + p.ratioH + ".");
        }
        asset.markUploaded(format.contentType, format.extension, bytes.length, d.width(), d.height(),
                sha256(bytes));
        return asset;
    }

    @Transactional
    public FileAssetView verify(UUID assetId, boolean approve, String reason) {
        FileAsset asset = load(assetId);
        UUID by = CurrentUser.requireId();
        Instant now = clock.instant();
        String before = asset.getVerificationStatus().name();
        if (approve) {
            asset.verify(by, now);
        } else {
            if (reason == null || reason.isBlank()) {
                throw ApiException.badRequest("REASON_REQUIRED", "Give a reason for rejecting the image.");
            }
            unpublishIfNeeded(asset);
            asset.reject(by, now, reason.trim());
        }
        audit.record(AuditEvent.of(approve ? "file.verified" : "file.rejected", "FileAsset", asset.getId())
                .inTournament(asset.getTournamentId())
                .withChange(Map.of("verification", before), Map.of("verification", asset.getVerificationStatus().name()))
                .withReason(approve ? null : reason));
        return view(asset);
    }

    /** Publishes a verified asset and returns its public URL. Idempotent. */
    @Transactional
    public String publish(UUID assetId) {
        FileAsset asset = load(assetId);
        if (asset.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw ApiException.unprocessable("FILE_NOT_VERIFIED", "Only verified images can be published.");
        }
        if (asset.getPublicObjectKey() != null) {
            return storage.publicUrl(asset.getPublicObjectKey());
        }
        FilePurpose p = asset.getPurpose();
        byte[] original = storage.readPrivate(asset.getObjectKey(), asset.getFileSize() + 1);
        byte[] encoded;
        try {
            encoded = inspector.reencode(original, p.publicFormat, p.maxPublicSide);
        } catch (IOException e) {
            throw ApiException.unprocessable("FILE_UNREADABLE", "The image couldn't be processed.");
        }
        // New random name per publish so CDN caches never serve a replaced image.
        String key = "t/" + asset.getTournamentId() + "/" + p.folder + "/" + Ids.newId() + "." + p.publicFormat.extension;
        storage.putPublic(key, encoded, p.publicFormat.contentType);
        asset.markPublished(key, clock.instant());
        return storage.publicUrl(key);
    }

    @Transactional
    public void unpublish(UUID assetId) {
        unpublishIfNeeded(load(assetId));
    }

    @Transactional(readOnly = true)
    public FileAssetView get(UUID assetId) {
        return view(load(assetId));
    }

    @Transactional(readOnly = true)
    public FileAsset require(UUID assetId, FilePurpose purpose) {
        FileAsset asset = load(assetId);
        if (asset.getPurpose() != purpose) {
            throw ApiException.badRequest("FILE_WRONG_PURPOSE", "This image was uploaded for a different use.");
        }
        if (asset.getUploadStatus() != UploadStatus.UPLOADED) {
            throw ApiException.unprocessable("FILE_NOT_READY", "This image hasn't finished uploading.");
        }
        return asset;
    }

    /** Staff views for many assets at once (list screens). */
    @Transactional(readOnly = true)
    public Map<UUID, FileAssetView> views(java.util.Collection<UUID> ids) {
        Map<UUID, FileAssetView> out = new java.util.HashMap<>();
        repository.findAllById(ids.stream().filter(java.util.Objects::nonNull).distinct().toList())
                .forEach(a -> out.put(a.getId(), view(a)));
        return out;
    }

    public Optional<String> publicUrl(UUID assetId) {
        if (assetId == null) {
            return Optional.empty();
        }
        return repository.findById(assetId)
                .map(FileAsset::getPublicObjectKey)
                .map(storage::publicUrl);
    }

    /** Removes uploads that were never completed (SRS §7.3 "abandoned temporary uploads"). */
    @Transactional
    public int purgeAbandoned(Duration olderThan) {
        Instant cutoff = clock.instant().minus(olderThan);
        List<FileAsset> stale = repository.findByUploadStatusAndCreatedAtBefore(UploadStatus.PENDING, cutoff, Limit.of(200));
        for (FileAsset a : stale) {
            try {
                storage.deletePrivate(a.getObjectKey());
            } catch (RuntimeException e) {
                log.debug("Could not delete abandoned object {}: {}", a.getObjectKey(), e.getMessage());
            }
            a.markDeleted(clock.instant());
        }
        return stale.size();
    }

    /** Removes registration uploads whose form was never submitted (kept 24 h for slow applicants). */
    @Transactional
    public int purgeOrphanedSessionUploads(Duration olderThan) {
        List<FileAsset> orphans = repository.findOrphanedSessionUploads(clock.instant().minus(olderThan));
        for (FileAsset a : orphans) {
            try {
                storage.deletePrivate(a.getObjectKey());
            } catch (RuntimeException e) {
                log.debug("Could not delete orphaned object {}: {}", a.getObjectKey(), e.getMessage());
            }
            a.markDeleted(clock.instant());
        }
        return orphans.size();
    }

    public FileAssetView view(FileAsset a) {
        String preview = a.getUploadStatus() == UploadStatus.UPLOADED
                ? storage.presignPrivateGet(a.getObjectKey(), PREVIEW_TTL)
                : null;
        String publicUrl = a.getPublicObjectKey() == null ? null : storage.publicUrl(a.getPublicObjectKey());
        return new FileAssetView(a.getId(), a.getPurpose(), a.getUploadStatus(), a.getVerificationStatus(),
                a.getContentType(), a.getFileSize(), a.getWidth(), a.getHeight(), a.getInvalidReason(),
                a.getRejectionReason(), preview, publicUrl, a.getPublishedAt());
    }

    private FileAsset load(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("File"));
    }

    private void unpublishIfNeeded(FileAsset asset) {
        if (asset.getPublicObjectKey() != null) {
            try {
                storage.deletePublic(asset.getPublicObjectKey());
            } catch (RuntimeException e) {
                log.warn("Could not delete public object {}: {}", asset.getPublicObjectKey(), e.getMessage());
            }
            asset.markUnpublished();
        }
    }

    private static void requireUploader(FileAsset asset) {
        if (asset.getUploadSessionId() != null) {
            throw ApiException.forbidden("FORBIDDEN", "This upload belongs to a public registration.");
        }
        UUID me = CurrentUser.get().map(CurrentUser.Actor::id).orElse(null);
        if (asset.getUploadedBy() != null && !asset.getUploadedBy().equals(me)) {
            throw ApiException.forbidden("FORBIDDEN", "You can only complete your own uploads.");
        }
    }

    private <T> T reject(FileAsset asset, String code, String message) {
        asset.markInvalid(message);
        try {
            storage.deletePrivate(asset.getObjectKey());
        } catch (RuntimeException e) {
            log.debug("Could not delete invalid object {}: {}", asset.getObjectKey(), e.getMessage());
        }
        throw ApiException.unprocessable(code, message);
    }

    static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sanitize(String filename) {
        if (filename == null) {
            return null;
        }
        String base = filename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 200 ? base.substring(0, 200) : base;
    }
}
