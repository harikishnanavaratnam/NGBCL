package lk.ngbcl.files.storage;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves {@link LocalDiskObjectStorage} in development. Every request is answered 404 unless
 * local-disk storage is the active backend, so this is inert in production.
 */
@RestController
public class DevStorageController {

    private final ObjectStorage storage;

    public DevStorageController(ObjectStorage storage) {
        this.storage = storage;
    }

    @PutMapping("/dev-storage/private/**")
    public ResponseEntity<Void> upload(HttpServletRequest req, @RequestParam long exp, @RequestParam String ct,
            @RequestParam long len, @RequestParam String sig) throws IOException {
        if (!(storage instanceof LocalDiskObjectStorage disk)) {
            return ResponseEntity.notFound().build();
        }
        String key = key(req, "/dev-storage/private/");
        String sentType = req.getContentType() == null ? "" : req.getContentType().split(";")[0].trim();
        if (!LocalDiskObjectStorage.isSafeKey(key) || !disk.verify("PUT", key, exp, ct, len, sig)
                || !sentType.equalsIgnoreCase(ct)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] body = req.getInputStream().readNBytes((int) Math.min(len + 1, 50L * 1024 * 1024));
        if (body.length != len) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        disk.writePrivate(key, body, ct);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dev-storage/private/**")
    public ResponseEntity<byte[]> preview(HttpServletRequest req, @RequestParam long exp, @RequestParam String sig) {
        if (!(storage instanceof LocalDiskObjectStorage disk)) {
            return ResponseEntity.notFound().build();
        }
        String key = key(req, "/dev-storage/private/");
        if (!LocalDiskObjectStorage.isSafeKey(key) || !disk.verify("GET", key, exp, "", 0, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return serve(disk, "private", key, CacheControl.noStore());
    }

    @GetMapping("/dev-storage/public/**")
    public ResponseEntity<byte[]> publicFile(HttpServletRequest req) {
        if (!(storage instanceof LocalDiskObjectStorage disk)) {
            return ResponseEntity.notFound().build();
        }
        String key = key(req, "/dev-storage/public/");
        if (!LocalDiskObjectStorage.isSafeKey(key)) {
            return ResponseEntity.notFound().build();
        }
        return serve(disk, "public", key, CacheControl.maxAge(Duration.ofHours(1)).cachePublic());
    }

    private static ResponseEntity<byte[]> serve(LocalDiskObjectStorage disk, String bucket, String key,
            CacheControl cache) {
        return disk.read(bucket, key)
                .map(f -> ResponseEntity.ok()
                        .cacheControl(cache)
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Cross-Origin-Resource-Policy", "cross-origin")
                        .contentType(MediaType.parseMediaType(f.contentType()))
                        .body(f.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static String key(HttpServletRequest req, String prefix) {
        String uri = req.getRequestURI();
        int i = uri.indexOf(prefix);
        return i < 0 ? "" : uri.substring(i + prefix.length());
    }
}
