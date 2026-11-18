package lk.ngbcl.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ngbcl.security")
public record SecurityProperties(
        String jwtSecret,
        String jwtIssuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean cookieSecure,
        String cookieDomain,
        List<String> allowedOrigins,
        String ipHashSalt) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "ngbcl.security.jwt-secret (NGBCL_JWT_SECRET) must be set and at least 32 bytes long");
        }
        if (jwtIssuer == null || jwtIssuer.isBlank()) {
            jwtIssuer = "ngbcl-api";
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(14);
        }
        if (allowedOrigins == null) {
            allowedOrigins = List.of();
        }
        if (ipHashSalt == null) {
            ipHashSalt = "";
        }
    }
}
