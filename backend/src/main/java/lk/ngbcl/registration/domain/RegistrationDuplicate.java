package lk.ngbcl.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;

/** A possible duplicate found at submission. Resolved by a person, never merged automatically (PLY-011). */
@Entity
@Table(name = "registration_duplicates")
public class RegistrationDuplicate extends UuidEntity {

    @Column(name = "registration_id", nullable = false)
    private UUID registrationId;

    @Column(name = "candidate_registration_id")
    private UUID candidateRegistrationId;

    @Column(name = "candidate_player_id")
    private UUID candidatePlayerId;

    /** Comma-separated: EMAIL, PHONE, NAME_DOB. */
    @Column(name = "matched_on", nullable = false, length = 60)
    private String matchedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", nullable = false, length = 20)
    private DuplicateResolution resolution = DuplicateResolution.OPEN;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RegistrationDuplicate() {
    }

    public RegistrationDuplicate(UUID registrationId, UUID candidateRegistrationId, UUID candidatePlayerId,
            String matchedOn, Instant now) {
        this.registrationId = registrationId;
        this.candidateRegistrationId = candidateRegistrationId;
        this.candidatePlayerId = candidatePlayerId;
        this.matchedOn = matchedOn;
        this.createdAt = now;
    }

    public void resolve(DuplicateResolution r, UUID by, Instant at) {
        this.resolution = r;
        this.resolvedBy = by;
        this.resolvedAt = at;
    }

    public UUID getRegistrationId() {
        return registrationId;
    }

    public UUID getCandidateRegistrationId() {
        return candidateRegistrationId;
    }

    public UUID getCandidatePlayerId() {
        return candidatePlayerId;
    }

    public String getMatchedOn() {
        return matchedOn;
    }

    public DuplicateResolution getResolution() {
        return resolution;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
