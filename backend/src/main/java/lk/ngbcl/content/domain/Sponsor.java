package lk.ngbcl.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

/** Sponsor shown on the title lockup, sponsor wall and auction ticker (CNT-005..007). */
@Entity
@Table(name = "sponsors")
public class Sponsor extends AuditedEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private SponsorTier tier;

    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    @Column(name = "website_url", length = 300)
    private String websiteUrl;

    @Column(name = "award_name", length = 160)
    private String awardName;

    @Column(name = "show_on_wall", nullable = false)
    private boolean showOnWall = true;

    @Column(name = "show_on_ticker", nullable = false)
    private boolean showOnTicker = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Sponsor() {
    }

    public Sponsor(UUID tournamentId, int displayOrder) {
        this.tournamentId = tournamentId;
        this.displayOrder = displayOrder;
    }

    public void edit(String name, SponsorTier tier, UUID logoAssetId, String websiteUrl, String awardName,
            boolean showOnWall, boolean showOnTicker, boolean active) {
        this.name = name;
        this.tier = tier;
        this.logoAssetId = logoAssetId;
        this.websiteUrl = websiteUrl;
        this.awardName = awardName;
        this.showOnWall = showOnWall;
        this.showOnTicker = showOnTicker;
        this.active = active;
    }

    public void moveTo(int order) {
        this.displayOrder = order;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public String getName() {
        return name;
    }

    public SponsorTier getTier() {
        return tier;
    }

    public UUID getLogoAssetId() {
        return logoAssetId;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getAwardName() {
        return awardName;
    }

    public boolean isShowOnWall() {
        return showOnWall;
    }

    public boolean isShowOnTicker() {
        return showOnTicker;
    }

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
