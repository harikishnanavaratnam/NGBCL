package lk.ngbcl.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived access tokens carrying the user's permissions as the {@code perms} claim. */
@Service
public class JwtService {

    public static final String PERMS_CLAIM = "perms";
    public static final String ROLES_CLAIM = "roles";
    public static final String NAME_CLAIM = "name";

    private final JwtEncoder encoder;
    private final SecurityProperties props;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, SecurityProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public record AccessToken(String value, long expiresInSeconds) {
    }

    public AccessToken issue(UUID userId, String displayName, Collection<String> roles,
            Collection<Permission> permissions) {
        Instant now = clock.instant();
        Instant exp = now.plus(props.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwtIssuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(exp)
                .claim(NAME_CLAIM, displayName)
                .claim(ROLES_CLAIM, List.copyOf(roles))
                .claim(PERMS_CLAIM, permissions.stream().map(Enum::name).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, props.accessTokenTtl().toSeconds());
    }
}
