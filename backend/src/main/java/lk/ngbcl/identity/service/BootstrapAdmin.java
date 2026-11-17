package lk.ngbcl.identity.service;

import java.util.Set;
import lk.ngbcl.identity.domain.Role;
import lk.ngbcl.identity.domain.RoleRepository;
import lk.ngbcl.identity.domain.User;
import lk.ngbcl.identity.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first Super Administrator from env vars when the users table is empty. */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String name;

    public BootstrapAdmin(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
            @Value("${ngbcl.bootstrap.admin-email:}") String email,
            @Value("${ngbcl.bootstrap.admin-password:}") String password,
            @Value("${ngbcl.bootstrap.admin-name:Super Admin}") String name) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (email == null || email.isBlank() || password == null || password.length() < UserAdminService.MIN_PASSWORD_LENGTH) {
            log.warn("No users exist. Set NGBCL_BOOTSTRAP_ADMIN_EMAIL and NGBCL_BOOTSTRAP_ADMIN_PASSWORD (12+ chars) "
                    + "to create the first Super Administrator.");
            return;
        }
        Role superAdmin = roles.findById(Role.SUPER_ADMIN).orElseThrow();
        User admin = new User(email, name, passwordEncoder.encode(password));
        admin.replaceRoles(Set.of(superAdmin));
        users.save(admin);
        log.info("Created bootstrap Super Administrator {}", admin.getEmail());
    }
}
