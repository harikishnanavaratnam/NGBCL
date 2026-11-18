package lk.ngbcl.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Who is calling (from the validated JWT). Empty for anonymous/public requests. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public record Actor(UUID id, String name) {
    }

    public static Optional<Actor> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            String name = jwt.getToken().getClaimAsString(JwtService.NAME_CLAIM);
            return Optional.of(new Actor(UUID.fromString(jwt.getToken().getSubject()), name == null ? "Unknown" : name));
        }
        return Optional.empty();
    }

    public static UUID requireId() {
        return get().map(Actor::id).orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
