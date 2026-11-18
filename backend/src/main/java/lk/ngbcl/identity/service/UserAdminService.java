package lk.ngbcl.identity.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.identity.domain.Role;
import lk.ngbcl.identity.domain.RoleRepository;
import lk.ngbcl.identity.domain.User;
import lk.ngbcl.identity.domain.UserRepository;
import lk.ngbcl.identity.domain.UserStatus;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff account management (Super Administrator only — USER_MANAGE). */
@Service
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UserAdminService {

    public static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;

    public UserAdminService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokens, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    public record UserSummary(UUID id, String email, String displayName, List<String> roles, UserStatus status,
            java.time.Instant lastLoginAt) {

        static UserSummary from(User u) {
            return new UserSummary(u.getId(), u.getEmail(), u.getDisplayName(), List.copyOf(u.roleCodes()), u.getStatus(),
                    u.getLastLoginAt());
        }
    }

    public record RoleSummary(String code, String name, String description, List<String> permissions) {
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> list(int page, int size) {
        var result = users.findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("displayName")));
        return PageResponse.of(result, UserSummary::from);
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> roles() {
        return roles.findAll(Sort.by("code")).stream()
                .map(r -> new RoleSummary(r.getCode(), r.getName(), r.getDescription(),
                        r.getPermissions().stream().map(Enum::name).sorted().toList()))
                .toList();
    }

    @Transactional
    public UserSummary create(String email, String displayName, String password, Set<String> roleCodes) {
        String normalized = User.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("EMAIL_IN_USE", "A user with this email already exists.");
        }
        validatePassword(password);
        User user = new User(normalized, displayName, passwordEncoder.encode(password));
        user.replaceRoles(resolveRoles(roleCodes));
        users.save(user);
        audit.record(AuditEvent.of("user.created", "User", user.getId())
                .withChange(null, Map.of("email", normalized, "roles", List.copyOf(user.roleCodes()))));
        return UserSummary.from(user);
    }

    @Transactional
    public UserSummary update(UUID id, String displayName, Set<String> roleCodes, UserStatus status) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        UUID me = CurrentUser.requireId();
        Map<String, Object> before = snapshot(user);

        if (displayName != null && !displayName.isBlank()) {
            user.rename(displayName);
        }
        if (roleCodes != null) {
            if (me.equals(id) && user.roleCodes().contains(Role.SUPER_ADMIN) && !roleCodes.contains(Role.SUPER_ADMIN)) {
                throw ApiException.unprocessable("CANNOT_DEMOTE_SELF", "You can't remove your own Super Administrator role.");
            }
            user.replaceRoles(resolveRoles(roleCodes));
        }
        if (status != null && status != user.getStatus()) {
            if (me.equals(id) && status == UserStatus.DISABLED) {
                throw ApiException.unprocessable("CANNOT_DISABLE_SELF", "You can't disable your own account.");
            }
            user.setStatus(status);
            if (status == UserStatus.DISABLED) {
                refreshTokens.revokeAll(id);
            }
        }
        audit.record(AuditEvent.of("user.updated", "User", id).withChange(before, snapshot(user)));
        return UserSummary.from(user);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        validatePassword(newPassword);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAll(id);
        audit.record(AuditEvent.of("user.password_reset", "User", id));
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw ApiException.badRequest("ROLE_REQUIRED", "Choose at least one role.");
        }
        Set<Role> found = new HashSet<>(roles.findAllById(codes));
        if (found.size() != codes.size()) {
            throw ApiException.badRequest("UNKNOWN_ROLE", "One or more roles don't exist.");
        }
        return found;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("WEAK_PASSWORD",
                    "Use a password of at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }

    private static Map<String, Object> snapshot(User u) {
        return Map.of("displayName", u.getDisplayName(), "roles", List.copyOf(u.roleCodes()), "status",
                u.getStatus().name());
    }
}
