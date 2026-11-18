package lk.ngbcl.identity.service;

import java.time.Clock;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.identity.domain.User;
import lk.ngbcl.identity.domain.UserRepository;
import lk.ngbcl.security.JwtService;
import lk.ngbcl.security.LoginRateLimiter;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.RequestInfo;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;
    private final RequestInfo requestInfo;
    private final Clock clock;
    /** Hash of a random value, checked when the email doesn't exist so timing doesn't reveal accounts. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
            RefreshTokenService refreshTokens, LoginRateLimiter rateLimiter, AuditService audit, RequestInfo requestInfo,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.requestInfo = requestInfo;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public record AuthResult(MeResponse user, String accessToken, long expiresIn, String refreshToken) {
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(String email, String password) {
        String normalized = User.normalizeEmail(email);
        String ip = requestInfo.ipHash();
        if (rateLimiter.isBlocked(ip, normalized)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many sign-in attempts. Wait a few minutes and try again.");
        }
        User user = users.findByEmail(normalized).orElse(null);
        boolean ok;
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            ok = false;
        } else {
            ok = passwordEncoder.matches(password, user.getPasswordHash()) && user.isActive();
        }
        if (!ok) {
            rateLimiter.recordFailure(ip, normalized);
            if (user != null) {
                audit.recordAs(user.getId(), user.getDisplayName(), AuditEvent.of("auth.login_failed", "User", user.getId()));
            }
            throw ApiException.unauthorized("BAD_CREDENTIALS", "Email or password is incorrect.");
        }
        rateLimiter.reset(ip, normalized);
        user.recordLogin(clock.instant());
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.changePasswordHash(passwordEncoder.encode(password));
        }
        audit.recordAs(user.getId(), user.getDisplayName(), AuditEvent.of("auth.login", "User", user.getId()));
        return tokensFor(user, refreshTokens.issue(user.getId()));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken);
        User user = users.findById(rotation.userId())
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("SESSION_EXPIRED", "Your session has ended. Please sign in again."));
        return tokensFor(user, rotation.newToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        return users.findById(userId)
                .filter(User::isActive)
                .map(MeResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("SESSION_EXPIRED", "Your session has ended. Please sign in again."));
    }

    private AuthResult tokensFor(User user, String refreshToken) {
        MeResponse me = MeResponse.from(user);
        JwtService.AccessToken access = jwtService.issue(user.getId(), user.getDisplayName(), me.roles(), user.permissions());
        return new AuthResult(me, access.value(), access.expiresInSeconds(), refreshToken);
    }
}
