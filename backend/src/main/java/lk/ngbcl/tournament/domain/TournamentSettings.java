package lk.ngbcl.tournament.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.web.ApiException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tournament-specific configuration (TRN-003, TRN-005). One row per tournament. */
@Entity
@Table(name = "tournament_settings")
public class TournamentSettings {

    @Id
    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "player_reg_opens_at")
    private Instant playerRegOpensAt;

    @Column(name = "player_reg_closes_at")
    private Instant playerRegClosesAt;

    @Column(name = "team_reg_opens_at")
    private Instant teamRegOpensAt;

    @Column(name = "team_reg_closes_at")
    private Instant teamRegClosesAt;

    @Column(name = "category_required", nullable = false)
    private boolean categoryRequired;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "player_fields", nullable = false, columnDefinition = "jsonb")
    private PlayerFieldRules playerFields = PlayerFieldRules.defaults();

    @Column(name = "dup_match_email", nullable = false)
    private boolean dupMatchEmail = true;

    @Column(name = "dup_match_phone", nullable = false)
    private boolean dupMatchPhone = true;

    @Column(name = "dup_match_name_dob", nullable = false)
    private boolean dupMatchNameDob;

    @Column(name = "max_photo_bytes", nullable = false)
    private int maxPhotoBytes = 5 * 1024 * 1024;

    @Column(name = "min_photo_width", nullable = false)
    private int minPhotoWidth = 600;

    @Column(name = "min_photo_height", nullable = false)
    private int minPhotoHeight = 750;

    @Column(name = "notify_on_registration", nullable = false)
    private boolean notifyOnRegistration;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase_override", length = 30)
    private TournamentPhase phaseOverride;

    @Column(name = "show_player_prices", nullable = false)
    private boolean showPlayerPrices = true;

    @Column(name = "show_team_budgets", nullable = false)
    private boolean showTeamBudgets;

    @Column(name = "facebook_url", length = 300)
    private String facebookUrl;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "prize_first")
    private Long prizeFirst;

    @Column(name = "prize_second")
    private Long prizeSecond;

    @Column(name = "prize_third")
    private Long prizeThird;

    @Column(name = "team_entry_fee")
    private Long teamEntryFee;

    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    @Column(name = "favicon_asset_id")
    private UUID faviconAssetId;

    @Column(name = "organiser_logo_asset_id")
    private UUID organiserLogoAssetId;

    @Column(name = "poster_asset_id")
    private UUID posterAssetId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected TournamentSettings() {
    }

    public TournamentSettings(UUID tournamentId) {
        this.tournamentId = tournamentId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    // ---- Phase & windows ---------------------------------------------------

    public WindowState playerWindow(Instant now, TournamentStatus status) {
        return gate(WindowState.of(playerRegOpensAt, playerRegClosesAt, now), status);
    }

    public WindowState teamWindow(Instant now, TournamentStatus status) {
        return gate(WindowState.of(teamRegOpensAt, teamRegClosesAt, now), status);
    }

    /** A draft tournament never accepts registrations, whatever the dates say. */
    private static WindowState gate(WindowState w, TournamentStatus status) {
        return status == TournamentStatus.DRAFT && w == WindowState.OPEN ? WindowState.NOT_OPEN : w;
    }

    public TournamentPhase phase(Instant now, TournamentStatus status) {
        if (phaseOverride != null) {
            return phaseOverride;
        }
        return switch (status) {
            case COMPLETED, ARCHIVED -> TournamentPhase.COMPLETED;
            case ACTIVE -> TournamentPhase.RUNNING;
            default -> switch (playerWindow(now, status)) {
                case OPEN -> TournamentPhase.REGISTRATION;
                case CLOSED -> TournamentPhase.REGISTRATION_CLOSED;
                default -> TournamentPhase.PRE_LAUNCH;
            };
        };
    }

    // ---- Updates -----------------------------------------------------------

    public void updateRegistration(Instant playerOpens, Instant playerCloses, Instant teamOpens, Instant teamCloses,
            boolean categoryRequired, PlayerFieldRules fields, boolean dupEmail, boolean dupPhone, boolean dupNameDob,
            int maxPhotoBytes, int minPhotoWidth, int minPhotoHeight, boolean notify) {
        requireWindow(playerOpens, playerCloses, "player");
        requireWindow(teamOpens, teamCloses, "team");
        if (!dupEmail && !dupPhone && !dupNameDob) {
            throw ApiException.badRequest("DUPLICATE_RULE_REQUIRED", "Keep at least one duplicate-matching rule on.");
        }
        this.playerRegOpensAt = playerOpens;
        this.playerRegClosesAt = playerCloses;
        this.teamRegOpensAt = teamOpens;
        this.teamRegClosesAt = teamCloses;
        this.categoryRequired = categoryRequired;
        this.playerFields = fields == null ? PlayerFieldRules.defaults() : fields;
        this.dupMatchEmail = dupEmail;
        this.dupMatchPhone = dupPhone;
        this.dupMatchNameDob = dupNameDob;
        this.maxPhotoBytes = maxPhotoBytes;
        this.minPhotoWidth = minPhotoWidth;
        this.minPhotoHeight = minPhotoHeight;
        this.notifyOnRegistration = notify;
    }

    public void updatePublicInfo(String facebookUrl, String contactEmail, String contactPhone, Long prizeFirst,
            Long prizeSecond, Long prizeThird, Long teamEntryFee, boolean showPlayerPrices, boolean showTeamBudgets,
            TournamentPhase phaseOverride) {
        this.facebookUrl = blankToNull(facebookUrl);
        this.contactEmail = blankToNull(contactEmail);
        this.contactPhone = blankToNull(contactPhone);
        this.prizeFirst = prizeFirst;
        this.prizeSecond = prizeSecond;
        this.prizeThird = prizeThird;
        this.teamEntryFee = teamEntryFee;
        this.showPlayerPrices = showPlayerPrices;
        this.showTeamBudgets = showTeamBudgets;
        this.phaseOverride = phaseOverride;
    }

    public void updateBranding(UUID logo, UUID favicon, UUID organiserLogo, UUID poster) {
        this.logoAssetId = logo;
        this.faviconAssetId = favicon;
        this.organiserLogoAssetId = organiserLogo;
        this.posterAssetId = poster;
    }

    private static void requireWindow(Instant opens, Instant closes, String who) {
        if ((opens == null) != (closes == null)) {
            throw ApiException.badRequest("WINDOW_INCOMPLETE", "Set both the opening and closing time for " + who + " registration.");
        }
        if (opens != null && !closes.isAfter(opens)) {
            throw ApiException.badRequest("WINDOW_INVALID", "The " + who + " registration must close after it opens.");
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    // ---- Getters -----------------------------------------------------------

    public UUID getTournamentId() {
        return tournamentId;
    }

    public Instant getPlayerRegOpensAt() {
        return playerRegOpensAt;
    }

    public Instant getPlayerRegClosesAt() {
        return playerRegClosesAt;
    }

    public Instant getTeamRegOpensAt() {
        return teamRegOpensAt;
    }

    public Instant getTeamRegClosesAt() {
        return teamRegClosesAt;
    }

    public boolean isCategoryRequired() {
        return categoryRequired;
    }

    public PlayerFieldRules getPlayerFields() {
        return playerFields;
    }

    public boolean isDupMatchEmail() {
        return dupMatchEmail;
    }

    public boolean isDupMatchPhone() {
        return dupMatchPhone;
    }

    public boolean isDupMatchNameDob() {
        return dupMatchNameDob;
    }

    public int getMaxPhotoBytes() {
        return maxPhotoBytes;
    }

    public int getMinPhotoWidth() {
        return minPhotoWidth;
    }

    public int getMinPhotoHeight() {
        return minPhotoHeight;
    }

    public boolean isNotifyOnRegistration() {
        return notifyOnRegistration;
    }

    public TournamentPhase getPhaseOverride() {
        return phaseOverride;
    }

    public boolean isShowPlayerPrices() {
        return showPlayerPrices;
    }

    public boolean isShowTeamBudgets() {
        return showTeamBudgets;
    }

    public String getFacebookUrl() {
        return facebookUrl;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public Long getPrizeFirst() {
        return prizeFirst;
    }

    public Long getPrizeSecond() {
        return prizeSecond;
    }

    public Long getPrizeThird() {
        return prizeThird;
    }

    public Long getTeamEntryFee() {
        return teamEntryFee;
    }

    public UUID getLogoAssetId() {
        return logoAssetId;
    }

    public UUID getFaviconAssetId() {
        return faviconAssetId;
    }

    public UUID getOrganiserLogoAssetId() {
        return organiserLogoAssetId;
    }

    public UUID getPosterAssetId() {
        return posterAssetId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
