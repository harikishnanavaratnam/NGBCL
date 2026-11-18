package lk.ngbcl.files.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare R2 (S3 API). Locally: MinIO from docker-compose (path-style URLs).
 * Leave {@code endpoint} empty to run without storage — upload endpoints then answer 503.
 */
@ConfigurationProperties(prefix = "ngbcl.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String privateBucket,
        String publicBucket,
        /** Base URL that serves the public bucket, e.g. https://cdn.ngbcl.lk (no trailing slash). */
        String publicBaseUrl,
        boolean pathStyle,
        Duration presignTtl,
        /** Development only: keeps files on disk and serves them from /dev-storage when no endpoint is set. */
        LocalDisk localDisk) {

    /**
     * @param baseUrl    public URL of this API (the browser uploads to {baseUrl}/dev-storage/...)
     * @param signingKey HMAC key for the dev-storage upload/preview links
     */
    public record LocalDisk(boolean enabled, String directory, String baseUrl, String signingKey) {

        public LocalDisk {
            if (directory == null || directory.isBlank()) {
                directory = "./.dev-storage";
            }
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "http://localhost:8080";
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
        }
    }

    public StorageProperties {
        if (region == null || region.isBlank()) {
            region = "auto";
        }
        if (presignTtl == null) {
            presignTtl = Duration.ofMinutes(10);
        }
        if (localDisk == null) {
            localDisk = new LocalDisk(false, null, null, null);
        }
        if (publicBaseUrl != null && publicBaseUrl.endsWith("/")) {
            publicBaseUrl = publicBaseUrl.substring(0, publicBaseUrl.length() - 1);
        }
    }

    public boolean localDiskMode() {
        return !configured() && localDisk.enabled();
    }

    public boolean configured() {
        return endpoint != null && !endpoint.isBlank();
    }
}
