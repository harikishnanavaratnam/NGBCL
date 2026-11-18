package lk.ngbcl.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Two cookies: the httpOnly refresh token (only sent to /api/auth/*) and a non-sensitive
 * "ngbcl_session" hint so the Next.js proxy can redirect anonymous visitors to /login.
 */
@Component
public class AuthCookies {

    public static final String REFRESH = "ngbcl_rt";
    public static final String HINT = "ngbcl_session";

    private final SecurityProperties props;

    public AuthCookies(SecurityProperties props) {
        this.props = props;
    }

    public ResponseCookie refresh(String token) {
        return base(REFRESH, token, props.refreshTokenTtl()).httpOnly(true).sameSite("Strict").path("/api/auth").build();
    }

    public ResponseCookie hint() {
        return base(HINT, "1", props.refreshTokenTtl()).httpOnly(false).sameSite("Lax").path("/").build();
    }

    public ResponseCookie clearRefresh() {
        return base(REFRESH, "", Duration.ZERO).httpOnly(true).sameSite("Strict").path("/api/auth").build();
    }

    public ResponseCookie clearHint() {
        return base(HINT, "", Duration.ZERO).httpOnly(false).sameSite("Lax").path("/").build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, value).secure(props.cookieSecure()).maxAge(maxAge);
        if (props.cookieDomain() != null && !props.cookieDomain().isBlank()) {
            b.domain(props.cookieDomain());
        }
        return b;
    }
}
