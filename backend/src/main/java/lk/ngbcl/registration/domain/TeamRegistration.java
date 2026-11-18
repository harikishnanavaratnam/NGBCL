package lk.ngbcl.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;
import lk.ngbcl.shared.web.ApiException;

/** A public team application (TEM-001..008). */
@Entity
@Table(name = "team_registrations")
public class TeamRegistration extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "registration_number", nullable = false, length = 20)
    private String registrationNumber;

    @Column(name = "team_name", nullable = false, length = 60)
    private String teamName;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "logo_asset_id", nullable = false)
    private UUID logoAssetId;

    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Column(name = "authorised_declared", nullable = false)
    private boolean authorisedDeclared;

    @Column(name = "fee_acknowledged", nullable = false)
    private boolean feeAcknowledged;

    @Column(name = "consent_at", nullable = false)
    private Instant consentAt;

    @Column(name = "consent_version", nullable = false, length = 20)
    private String consentVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RegistrationStatus status = RegistrationStatus.PENDING;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 120)
    private String rejectionReason;

    @Column(name = "rejection_message", length = 500)
    private String rejectionMessage;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    protected TeamRegistration() {
    }

    public TeamRegistration(UUID tournamentId, String registrationNumber, String teamName, String description,
            UUID logoAssetId, String contactName, String contactEmail, String contactPhone, String consentVersion,
            Instant now, String ipHash) {
        this.tournamentId = tournamentId;
        this.registrationNumber = registrationNumber;
        this.teamName = teamName.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.logoAssetId = logoAssetId;
        this.contactName = contactName.trim();
        this.contactEmail = contactEmail.trim().toLowerCase(java.util.Locale.ROOT);
        this.contactPhone = contactPhone;
        this.authorisedDeclared = true;
        this.feeAcknowledged = true;
        this.consentAt = now;
        this.consentVersion = consentVersion;
        this.submittedAt = now;
        this.ipHash = ipHash;
    }

    public void approve(UUID teamId, UUID by, Instant at) {
        requirePending();
        this.teamId = teamId;
        this.status = RegistrationStatus.APPROVED;
        this.reviewedBy = by;
        this.reviewedAt = at;
    }

    public void reject(String reason, String message, UUID by, Instant at) {
        requirePending();
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "Choose a rejection reason.");
        }
        this.status = RegistrationStatus.REJECTED;
        this.rejectionReason = reason.trim();
        this.rejectionMessage = message == null || message.isBlank() ? null : message.trim();
        this.reviewedBy = by;
        this.reviewedAt = at;
    }

    private void requirePending() {
        if (status != RegistrationStatus.PENDING) {
            throw ApiException.conflict("REGISTRATION_NOT_PENDING",
                    "This registration has already been " + status.name().toLowerCase() + ".");
        }
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getDescription() {
        return description;
    }

    public UUID getLogoAssetId() {
        return logoAssetId;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getRejectionMessage() {
        return rejectionMessage;
    }
}
