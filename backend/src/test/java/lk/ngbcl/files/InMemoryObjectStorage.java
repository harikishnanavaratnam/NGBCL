package lk.ngbcl.files;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lk.ngbcl.files.storage.ObjectStorage;

/** Test double for R2: "uploads" are simulated by calling {@link #simulateUpload}. */
public class InMemoryObjectStorage implements ObjectStorage {

    public static final String PREFIX = "memory://private/";

    public final Map<String, byte[]> privateObjects = new ConcurrentHashMap<>();
    public final Map<String, byte[]> publicObjects = new ConcurrentHashMap<>();

    public void simulateUpload(String uploadUrl, byte[] bytes) {
        privateObjects.put(uploadUrl.substring(PREFIX.length()), bytes);
    }

    @Override
    public PresignedUpload presignPrivatePut(String key, String contentType, long contentLength, Duration ttl) {
        return new PresignedUpload(PREFIX + key, Map.of("content-type", contentType), Instant.now().plus(ttl));
    }

    @Override
    public Optional<ObjectInfo> headPrivate(String key) {
        return Optional.ofNullable(privateObjects.get(key)).map(b -> new ObjectInfo(b.length, null));
    }

    @Override
    public byte[] readPrivate(String key, long maxBytes) {
        byte[] b = privateObjects.get(key);
        return b == null ? new byte[0] : Arrays.copyOf(b, (int) Math.min(b.length, maxBytes));
    }

    @Override
    public String presignPrivateGet(String key, Duration ttl) {
        return PREFIX + key + "?preview";
    }

    @Override
    public void deletePrivate(String key) {
        privateObjects.remove(key);
    }

    @Override
    public void putPublic(String key, byte[] bytes, String contentType) {
        publicObjects.put(key, bytes);
    }

    @Override
    public void deletePublic(String key) {
        publicObjects.remove(key);
    }

    @Override
    public String publicUrl(String key) {
        return "https://cdn.test/" + key;
    }
}
