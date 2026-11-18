package lk.ngbcl.team.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

/** A tournament team — approved public application or created by an admin (TEM-008..013). */
@Entity
@Table(name = "teams")
public class Team extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "team_registration_id")
    private UUID teamRegistrationId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "short_code", nullable = false, length = 3)
    private String shortCode;

    @Column(name = "color_slot")
    private Short colorSlot;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_source", nullable = false, length = 30)
    private TeamSource creationSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TeamStatus status = TeamStatus.ACTIVE;

    @Column(name = "fee_received", nullable = false)
    private boolean feeReceived;

    @Column(name = "fee_received_at")
    private Instant feeReceivedAt;

    @Column(name = "fee_received_by")
    private UUID feeReceivedBy;

    @Column(name = "created_by")
    private UUID createdBy;

    protected Team() {
    }

    public Team(UUID tournamentId, UUID teamRegistrationId, TeamSource source, String name, String slug,
            String shortCode, Short colorSlot, UUID createdBy) {
        this.tournamentId = tournamentId;
        this.teamRegistrationId = teamRegistrationId;
        this.creationSource = source;
        this.name = name.trim();
        this.slug = slug;
        this.shortCode = shortCode;
        this.colorSlot = colorSlot;
        this.createdBy = createdBy;
    }

    public void edit(String name, String slug, String shortCode, Short colorSlot, String description) {
        this.name = name.trim();
        this.slug = slug;
        this.shortCode = shortCode;
        this.colorSlot = colorSlot;
        this.description = description == null || description.isBlank() ? null : description.trim();
    }

    public void setContact(String name, String email, String phone) {
        this.contactName = blank(name);
        this.contactEmail = email == null || email.isBlank() ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        this.contactPhone = blank(phone);
    }

    public void setLogo(UUID assetId) {
        this.logoAssetId = assetId;
    }

    public void setFeeReceived(boolean received, UUID by, Instant at) {
        if (received == feeReceived) {
            return;
        }
        this.feeReceived = received;
        this.feeReceivedAt = received ? at : null;
        this.feeReceivedBy = received ? by : null;
    }

    public void setStatus(TeamStatus status) {
        this.status = status;
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public UUID getTeamRegistrationId() {
        return teamRegistrationId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Short getColorSlot() {
        return colorSlot;
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

    public TeamSource getCreationSource() {
        return creationSource;
    }

    public TeamStatus getStatus() {
        return status;
    }

    public boolean isFeeReceived() {
        return feeReceived;
    }

    public Instant getFeeReceivedAt() {
        return feeReceivedAt;
    }
}
