package lk.ngbcl.files.storage;

import java.time.Duration;
import java.util.Optional;
import lk.ngbcl.shared.web.ApiException;
import org.springframework.http.HttpStatus;

/** Used when no storage endpoint is configured: the app runs, uploads answer 503. */
public class UnconfiguredObjectStorage implements ObjectStorage {

    private static ApiException off() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED",
                "File storage isn't configured on this server yet.");
    }

    @Override
    public PresignedUpload presignPrivatePut(String key, String contentType, long contentLength, Duration ttl) {
        throw off();
    }

    @Override
    public Optional<ObjectInfo> headPrivate(String key) {
        throw off();
    }

    @Override
    public byte[] readPrivate(String key, long maxBytes) {
        throw off();
    }

    @Override
    public String presignPrivateGet(String key, Duration ttl) {
        throw off();
    }

    @Override
    public void deletePrivate(String key) {
        // nothing stored
    }

    @Override
    public void putPublic(String key, byte[] bytes, String contentType) {
        throw off();
    }

    @Override
    public void deletePublic(String key) {
        // nothing stored
    }

    @Override
    public String publicUrl(String key) {
        return null;
    }
}
