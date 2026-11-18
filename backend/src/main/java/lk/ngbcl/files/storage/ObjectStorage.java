package lk.ngbcl.files.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Thin port over R2 so the file rules can be tested without a network. */
public interface ObjectStorage {

    record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {
    }

    record ObjectInfo(long size, String contentType) {
    }

    /** Short-lived PUT URL into the PRIVATE bucket, bound to this content type and exact size. */
    PresignedUpload presignPrivatePut(String key, String contentType, long contentLength, Duration ttl);

    Optional<ObjectInfo> headPrivate(String key);

    /** Reads at most {@code maxBytes} bytes of a private object. */
    byte[] readPrivate(String key, long maxBytes);

    /** Short-lived GET URL so staff can preview a private image. */
    String presignPrivateGet(String key, Duration ttl);

    void deletePrivate(String key);

    void putPublic(String key, byte[] bytes, String contentType);

    void deletePublic(String key);

    String publicUrl(String key);
}
