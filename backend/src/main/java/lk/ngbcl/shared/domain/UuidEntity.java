package lk.ngbcl.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Base for every entity: the id is assigned on construction (UUID v7) so it can be referenced
 * before flush; {@link Persistable#isNew()} keeps Spring Data from issuing a merge-select.
 */
@MappedSuperclass
public abstract class UuidEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @Transient
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(id, ((UuidEntity) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
