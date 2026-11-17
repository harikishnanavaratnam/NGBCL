package lk.ngbcl.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.ngbcl.identity.service.AuthService;
import lk.ngbcl.identity.service.MeResponse;
import lk.ngbcl.security.AuthCookies;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.security.SecurityProperties;
import lk.ngbcl.shared.web.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Staff authentication. Only staff sign in — players, teams and visitors never need an account. */
@RestController
public class AuthController {

    private final AuthService auth;
    private final AuthCookies cookies;
    private final SecurityProperties props;

    public AuthController(AuthService auth, AuthCookies cookies, SecurityProperties props) {
        this.auth = auth;
        this.cookies = cookies;
        this.props = props;
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {
    }

    public record TokenResponse(String accessToken, long expiresIn, MeResponse user) {
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest req) {
        requireTrustedOrigin(req);
        return withSession(auth.login(body.email(), body.password()));
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<TokenResponse> refresh(@CookieValue(name = AuthCookies.REFRESH, required = false) String token,
            HttpServletRequest req, HttpServletResponse res) {
        requireTrustedOrigin(req);
        try {
            return withSession(auth.refresh(token));
        } catch (ApiException e) {
            if (!"REFRESH_RACE".equals(e.getCode())) {
                clearSession(res);
            }
            throw e;
        }
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH, required = false) String token,
            HttpServletRequest req) {
        requireTrustedOrigin(req);
        auth.logout(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearHint().toString())
                .build();
    }

    @GetMapping("/api/me")
    public MeResponse me() {
        return auth.me(CurrentUser.requireId());
    }

    private ResponseEntity<TokenResponse> withSession(AuthService.AuthResult r) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(r.refreshToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.hint().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TokenResponse(r.accessToken(), r.expiresIn(), r.user()));
    }

    private void clearSession(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString());
        res.addHeader(HttpHeaders.SET_COOKIE, cookies.clearHint().toString());
    }

    /**
     * CSRF defence for the cookie-authenticated endpoints: browsers always send Origin on
     * cross-site POSTs, so reject any Origin that isn't one of our frontends.
     */
    private void requireTrustedOrigin(HttpServletRequest req) {
        String origin = req.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !props.allowedOrigins().contains(origin)) {
            throw ApiException.forbidden("UNTRUSTED_ORIGIN", "Requests from this site are not allowed.");
        }
    }
}
