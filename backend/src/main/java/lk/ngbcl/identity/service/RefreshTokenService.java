package lk.ngbcl.identity.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lk.ngbcl.identity.domain.RefreshToken;
import lk.ngbcl.identity.domain.RefreshTokenRepository;
import lk.ngbcl.security.SecurityProperties;
import lk.ngbcl.shared.domain.Ids;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.RequestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotating refresh tokens with reuse detection:
 * - every refresh consumes the presented token and issues a new one in the same family;
 * - presenting an already-rotated token again (outside a short race window) revokes the
 *   whole family, signing out a possibly stolen session.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    /** Two tabs refreshing at once is normal; don't treat that as theft. */
    static final Duration RACE_GRACE = Duration.ofSeconds(10);

    private final RefreshTokenRepository repository;
    private final SecurityProperties props;
    private final RequestInfo requestInfo;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties props, RequestInfo requestInfo,
            Clock clock) {
        this.repository = repository;
        this.props = props;
        this.requestInfo = requestInfo;
        this.clock = clock;
    }

    public record Rotation(UUID userId, String newToken) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String issue(UUID userId) {
        return issueInFamily(userId, Ids.newId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalid();
        }
        Instant now = clock.instant();
        RefreshToken token = repository.findForRotation(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);

        if (token.getRevokedAt() != null || token.isExpired(now)) {
            throw invalid();
        }
        if (token.getRotatedAt() != null) {
            if (token.getRotatedAt().plus(RACE_GRACE).isAfter(now)) {
                // Benign race: the other request already set the new cookie; the client retries once.
                throw ApiException.conflict("REFRESH_RACE", "Session is being refreshed. Retry.");
            }
            log.warn("Refresh token reuse detected for user {} — revoking family {}", token.getUserId(),
                    token.getFamilyId());
            repository.revokeFamily(token.getFamilyId(), now);
            throw invalid();
        }
        token.markRotated(now);
        return new Rotation(token.getUserId(), issueInFamily(token.getUserId(), token.getFamilyId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findForRotation(hash(rawToken)).ifPresent(t -> repository.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    private String issueInFamily(UUID userId, UUID familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        repository.save(new RefreshToken(userId, familyId, hash(raw), now, now.plus(props.refreshTokenTtl()),
                requestInfo.userAgent(), requestInfo.ipHash()));
        return raw;
    }

    static String hash(String raw) {
        return RequestInfo.sha256Hex(raw);
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("SESSION_EXPIRED", "Your session has ended. Please sign in again.");
    }
}
