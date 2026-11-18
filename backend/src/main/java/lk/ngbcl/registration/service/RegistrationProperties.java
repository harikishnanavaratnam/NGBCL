package lk.ngbcl.registration.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ngbcl.registration")
public record RegistrationProperties(String turnstileSecret, String turnstileSiteKey, String consentVersion,
        Duration sessionTtl, int sessionsPerIpPerHour) {

    public RegistrationProperties {
        if (consentVersion == null || consentVersion.isBlank()) {
            consentVersion = "2026-1";
        }
        if (sessionTtl == null) {
            sessionTtl = Duration.ofHours(2);
        }
        if (sessionsPerIpPerHour <= 0) {
            sessionsPerIpPerHour = 30;
        }
    }

    public boolean turnstileEnabled() {
        return turnstileSecret != null && !turnstileSecret.isBlank();
    }
}
