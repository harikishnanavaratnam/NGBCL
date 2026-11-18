package lk.ngbcl.shared.site;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Public website (Next.js) on-demand revalidation. Empty URL = disabled; pages then refresh on
 * their normal cache lifetime (a few minutes).
 */
@ConfigurationProperties(prefix = "ngbcl.site")
public record SiteProperties(String revalidateUrl, String revalidateSecret) {

    public boolean enabled() {
        return revalidateUrl != null && !revalidateUrl.isBlank() && revalidateSecret != null && !revalidateSecret.isBlank();
    }
}
