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
        Duration presignTtl) {

    public StorageProperties {
        if (region == null || region.isBlank()) {
            region = "auto";
        }
        if (presignTtl == null) {
            presignTtl = Duration.ofMinutes(10);
        }
        if (publicBaseUrl != null && publicBaseUrl.endsWith("/")) {
            publicBaseUrl = publicBaseUrl.substring(0, publicBaseUrl.length() - 1);
        }
    }

    public boolean configured() {
        return endpoint != null && !endpoint.isBlank();
    }
}
