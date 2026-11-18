package lk.ngbcl.identity.service;

import java.util.List;
import java.util.UUID;
import lk.ngbcl.identity.domain.User;

public record MeResponse(UUID id, String email, String displayName, List<String> roles, List<String> permissions,
        HomeSurface homeSurface) {

    public static MeResponse from(User u) {
        var perms = u.permissions();
        return new MeResponse(u.getId(), u.getEmail(), u.getDisplayName(), List.copyOf(u.roleCodes()),
                perms.stream().map(Enum::name).sorted().toList(), HomeSurface.of(perms));
    }
}
