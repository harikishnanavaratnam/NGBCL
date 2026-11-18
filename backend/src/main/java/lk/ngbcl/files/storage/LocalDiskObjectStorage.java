package lk.ngbcl.files.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Development stand-in for R2: files on local disk, uploads/previews through signed
 * {@code /dev-storage} links served by {@link DevStorageController}. Never enabled when a real
 * storage endpoint is configured.
 */
public class LocalDiskObjectStorage implements ObjectStorage {

    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9/._-]{0,299}$");

    private final Path root;
    private final String baseUrl;
    private final byte[] signingKey;

    public LocalDiskObjectStorage(StorageProperties.LocalDisk cfg) {
        this.root = Path.of(cfg.directory()).toAbsolutePath().normalize();
        this.baseUrl = cfg.baseUrl();
        String key = cfg.signingKey() == null || cfg.signingKey().isBlank()
                ? java.util.UUID.randomUUID().toString()
                : cfg.signingKey();
        this.signingKey = key.getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(root.resolve("private"));
            Files.createDirectories(root.resolve("public"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- ObjectStorage -------------------------------------------------------

    @Override
    public PresignedUpload presignPrivatePut(String key, String contentType, long contentLength, Duration ttl) {
        Instant exp = Instant.now().plus(ttl);
        String sig = sign("PUT", key, exp.getEpochSecond(), contentType, contentLength);
        String url = baseUrl + "/dev-storage/private/" + key + "?exp=" + exp.getEpochSecond() + "&ct="
                + URLEncoder.encode(contentType, StandardCharsets.UTF_8) + "&len=" + contentLength + "&sig=" + sig;
        return new PresignedUpload(url, Map.of("Content-Type", contentType), exp);
    }

    @Override
    public Optional<ObjectInfo> headPrivate(String key) {
        Path p = resolve("private", key);
        if (!Files.isRegularFile(p)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ObjectInfo(Files.size(p), contentTypeOf(p)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] readPrivate(String key, long maxBytes) {
        try (InputStream in = Files.newInputStream(resolve("private", key))) {
            return in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String presignPrivateGet(String key, Duration ttl) {
        long exp = Instant.now().plus(ttl).getEpochSecond();
        return baseUrl + "/dev-storage/private/" + key + "?exp=" + exp + "&sig=" + sign("GET", key, exp, "", 0);
    }

    @Override
    public void deletePrivate(String key) {
        delete(resolve("private", key));
    }

    @Override
    public void putPublic(String key, byte[] bytes, String contentType) {
        write(resolve("public", key), bytes, contentType);
    }

    @Override
    public void deletePublic(String key) {
        delete(resolve("public", key));
    }

    @Override
    public String publicUrl(String key) {
        return baseUrl + "/dev-storage/public/" + key;
    }

    // ---- Used by DevStorageController ---------------------------------------------

    public boolean verify(String method, String key, long exp, String contentType, long length, String sig) {
        if (sig == null || Instant.now().getEpochSecond() > exp) {
            return false;
        }
        String expected = sign(method, key, exp, contentType == null ? "" : contentType, length);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8));
    }

    public void writePrivate(String key, byte[] bytes, String contentType) {
        write(resolve("private", key), bytes, contentType);
    }

    public Optional<StoredFile> read(String bucket, String key) {
        Path p = resolve(bucket, key);
        if (!Files.isRegularFile(p)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StoredFile(Files.readAllBytes(p), contentTypeOf(p)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record StoredFile(byte[] bytes, String contentType) {
    }

    public static boolean isSafeKey(String key) {
        return key != null && SAFE_KEY.matcher(key).matches() && !key.contains("..") && !key.contains("//");
    }

    // ---- Internals -----------------------------------------------------------

    private Path resolve(String bucket, String key) {
        if (!isSafeKey(key)) {
            throw new IllegalArgumentException("Unsafe storage key");
        }
        Path base = root.resolve(bucket);
        Path p = base.resolve(key).normalize();
        if (!p.startsWith(base)) {
            throw new IllegalArgumentException("Unsafe storage key");
        }
        return p;
    }

    private static void write(Path p, byte[] bytes, String contentType) {
        try {
            Files.createDirectories(p.getParent());
            Files.write(p, bytes);
            Files.writeString(sidecar(p), contentType == null ? "application/octet-stream" : contentType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path p) {
        try {
            Files.deleteIfExists(p);
            Files.deleteIfExists(sidecar(p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String contentTypeOf(Path p) throws IOException {
        Path meta = sidecar(p);
        return Files.isRegularFile(meta) ? Files.readString(meta).trim() : "application/octet-stream";
    }

    private static Path sidecar(Path p) {
        return p.resolveSibling(p.getFileName() + ".content-type");
    }

    private String sign(String method, String key, long exp, String contentType, long length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            String payload = method + "\n" + key + "\n" + exp + "\n" + contentType + "\n" + length;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
