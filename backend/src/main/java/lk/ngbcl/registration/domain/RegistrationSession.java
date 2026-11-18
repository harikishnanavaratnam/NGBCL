package lk.ngbcl.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lk.ngbcl.shared.domain.UuidEntity;

/** Anonymous applicant session that scopes uploads before submission (SRS §7.2). */
@Entity
@Table(name = "registration_sessions")
public class RegistrationSession extends UuidEntity {

    public static final int MAX_UPLOADS = 6;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private RegistrationKind kind;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "uploads", nullable = false)
    private int uploads;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected RegistrationSession() {
    }

    public RegistrationSession(RegistrationKind kind, String tokenHash, String ipHash, Instant now, Instant expiresAt) {
        this.kind = kind;
        this.tokenHash = tokenHash;
        this.ipHash = ipHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public boolean canUpload() {
        return uploads < MAX_UPLOADS;
    }

    public void countUpload() {
        uploads++;
    }

    public void markUsed(Instant at) {
        usedAt = at;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public RegistrationKind getKind() {
        return kind;
    }
}
