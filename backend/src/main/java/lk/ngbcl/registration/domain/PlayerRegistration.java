package lk.ngbcl.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;
import lk.ngbcl.shared.web.ApiException;

/** A player's application for one tournament (SRS §6.2–6.3). Submitted data is kept as typed columns. */
@Entity
@Table(name = "player_registrations")
public class PlayerRegistration extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "registration_number", nullable = false, length = 20)
    private String registrationNumber;

    @Column(name = "player_id")
    private UUID playerId;

    /** Existing player chosen while resolving a duplicate; used on approval. */
    @Column(name = "linked_player_id")
    private UUID linkedPlayerId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "home_area", length = 80)
    private String homeArea;

    @Column(name = "experience", length = 300)
    private String experience;

    @Column(name = "emergency_name", length = 120)
    private String emergencyName;

    @Column(name = "emergency_phone", length = 30)
    private String emergencyPhone;

    @Column(name = "skill_category_id")
    private UUID skillCategoryId;

    @Column(name = "photo_asset_id", nullable = false)
    private UUID photoAssetId;

    @Column(name = "jaffna_declared", nullable = false)
    private boolean jaffnaDeclared;

    @Column(name = "consent_at", nullable = false)
    private Instant consentAt;

    @Column(name = "consent_version", nullable = false, length = 20)
    private String consentVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RegistrationStatus status = RegistrationStatus.PENDING;

    @Column(name = "duplicate_flag", nullable = false)
    private boolean duplicateFlag;

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

    protected PlayerRegistration() {
    }

    public PlayerRegistration(UUID tournamentId, String registrationNumber, PlayerDetails d, UUID photoAssetId,
            String consentVersion, Instant now, String ipHash) {
        this.tournamentId = tournamentId;
        this.registrationNumber = registrationNumber;
        apply(d);
        this.photoAssetId = photoAssetId;
        this.jaffnaDeclared = true;
        this.consentAt = now;
        this.consentVersion = consentVersion;
        this.submittedAt = now;
        this.ipHash = ipHash;
    }

    /** Applicant-supplied fields. Also used for audited admin edits (PLY-013). */
    public record PlayerDetails(String fullName, LocalDate dateOfBirth, String gender, String email, String phone,
            String address, String homeArea, String experience, String emergencyName, String emergencyPhone,
            UUID skillCategoryId) {
    }

    public PlayerDetails details() {
        return new PlayerDetails(fullName, dateOfBirth, gender, email, phone, address, homeArea, experience,
                emergencyName, emergencyPhone, skillCategoryId);
    }

    public void edit(PlayerDetails d) {
        requirePending();
        apply(d);
    }

    private void apply(PlayerDetails d) {
        this.fullName = d.fullName().trim();
        this.dateOfBirth = d.dateOfBirth();
        this.gender = d.gender();
        this.email = d.email().trim().toLowerCase(java.util.Locale.ROOT);
        this.phone = d.phone();
        this.address = d.address();
        this.homeArea = d.homeArea();
        this.experience = d.experience();
        this.emergencyName = d.emergencyName();
        this.emergencyPhone = d.emergencyPhone();
        this.skillCategoryId = d.skillCategoryId();
    }

    public void flagDuplicate(boolean flag) {
        this.duplicateFlag = flag;
    }

    public void linkTo(UUID existingPlayerId) {
        requirePending();
        this.linkedPlayerId = existingPlayerId;
    }

    public void approve(UUID playerId, UUID by, Instant at) {
        requirePending();
        this.playerId = playerId;
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

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getLinkedPlayerId() {
        return linkedPlayerId;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getHomeArea() {
        return homeArea;
    }

    public String getExperience() {
        return experience;
    }

    public String getEmergencyName() {
        return emergencyName;
    }

    public String getEmergencyPhone() {
        return emergencyPhone;
    }

    public UUID getSkillCategoryId() {
        return skillCategoryId;
    }

    public UUID getPhotoAssetId() {
        return photoAssetId;
    }

    public boolean isJaffnaDeclared() {
        return jaffnaDeclared;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public boolean isDuplicateFlag() {
        return duplicateFlag;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
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
