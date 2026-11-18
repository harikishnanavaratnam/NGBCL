package lk.ngbcl.tournament.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lk.ngbcl.shared.domain.AuditedEntity;
import lk.ngbcl.shared.web.ApiException;

/** A tournament edition (SRS TRN-001..006). Everything else is scoped to one of these. */
@Entity
@Table(name = "tournaments")
public class Tournament extends AuditedEntity {

    @Column(name = "slug", nullable = false, length = 60)
    private String slug;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "short_name", nullable = false, length = 40)
    private String shortName;

    @Column(name = "edition", nullable = false, length = 20)
    private String edition;

    @Column(name = "description")
    private String description;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "venue", length = 200)
    private String venue;

    @Column(name = "city", length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TournamentStatus status = TournamentStatus.DRAFT;

    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone = "Asia/Colombo";

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LKR";

    @Column(name = "is_current", nullable = false)
    private boolean currentEdition;

    protected Tournament() {
    }

    public void updateDetails(String name, String shortName, String description, LocalDate startsOn, LocalDate endsOn,
            String venue, String city) {
        if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) {
            throw ApiException.badRequest("INVALID_DATES", "The end date must be on or after the start date.");
        }
        this.name = name.trim();
        this.shortName = shortName.trim();
        this.description = description;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.venue = venue == null || venue.isBlank() ? null : venue.trim();
        this.city = city == null || city.isBlank() ? null : city.trim();
    }

    public void changeStatus(TournamentStatus next) {
        this.status = next;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getEdition() {
        return edition;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public String getVenue() {
        return venue;
    }

    public String getCity() {
        return city;
    }

    public TournamentStatus getStatus() {
        return status;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isCurrentEdition() {
        return currentEdition;
    }
}
