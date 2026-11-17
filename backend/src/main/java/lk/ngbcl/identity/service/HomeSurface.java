package lk.ngbcl.identity.service;

import java.util.EnumSet;
import java.util.Set;
import lk.ngbcl.security.Permission;

/** Where a user lands after sign-in (brief §6: "every role lands on exactly one home surface"). */
public enum HomeSurface {
    ADMIN,
    AUCTION_CONSOLE,
    SCORER;

    private static final Set<Permission> NON_ADMIN = EnumSet.of(Permission.TOURNAMENT_VIEW, Permission.AUCTION_CONTROL,
            Permission.MATCH_SCORE);

    public static HomeSurface of(Set<Permission> perms) {
        boolean hasAdminWork = perms.stream().anyMatch(p -> !NON_ADMIN.contains(p));
        if (hasAdminWork) {
            return ADMIN;
        }
        if (perms.contains(Permission.AUCTION_CONTROL)) {
            return AUCTION_CONSOLE;
        }
        if (perms.contains(Permission.MATCH_SCORE)) {
            return SCORER;
        }
        return ADMIN;
    }
}
