package lk.ngbcl.identity.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import lk.ngbcl.security.Permission;

/** Staff role with its permissions (seeded by Flyway; not editable from the UI in edition 1). */
@Entity
@Table(name = "roles")
public class Role {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    @Id
    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_code"))
    @Column(name = "permission", length = 40)
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<Permission> getPermissions() {
        return permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
    }
}
