package lk.ngbcl.identity.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lk.ngbcl.identity.domain.UserRepository;
import lk.ngbcl.identity.domain.UserStatus;
import lk.ngbcl.security.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Active staff holding a permission — e.g. who can be assigned to score a match. No contact data. */
@Service
public class StaffDirectory {

    private final UserRepository users;

    public StaffDirectory(UserRepository users) {
        this.users = users;
    }

    public record Staff(UUID id, String displayName) {
    }

    @Transactional(readOnly = true)
    public List<Staff> withAnyPermission(Set<Permission> permissions) {
        return users.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .filter(u -> u.getRoles().stream().flatMap(r -> r.getPermissions().stream()).anyMatch(permissions::contains))
                .map(u -> new Staff(u.getId(), u.getDisplayName()))
                .sorted(Comparator.comparing(Staff::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
