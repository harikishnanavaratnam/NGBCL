package lk.ngbcl.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;
import org.hibernate.annotations.Immutable;

/** Internal reviewer note (PLY-022). Never exposed by public endpoints. */
@Entity
@Immutable
@Table(name = "registration_notes")
public class RegistrationNote extends UuidEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_kind", nullable = false, length = 10)
    private RegistrationKind registrationKind;

    @Column(name = "registration_id", nullable = false)
    private UUID registrationId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 120)
    private String authorName;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RegistrationNote() {
    }

    public RegistrationNote(RegistrationKind kind, UUID registrationId, UUID authorId, String authorName, String body,
            Instant now) {
        this.registrationKind = kind;
        this.registrationId = registrationId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.body = body;
        this.createdAt = now;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
