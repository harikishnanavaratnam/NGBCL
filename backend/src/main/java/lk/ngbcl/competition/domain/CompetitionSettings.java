package lk.ngbcl.competition.domain;

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

/** Format and scheduling rules for an edition (FIX-001, FIX-009). All admin-editable. */
@Entity
@Table(name = "competition_settings")
public class CompetitionSettings {

    @Id
    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 24)
    private CompetitionFormat format = CompetitionFormat.GROUPS_KNOCKOUT;

    @Column(name = "group_count", nullable = false)
    private int groupCount = 2;

    @Column(name = "qualifiers_per_group", nullable = false)
    private int qualifiersPerGroup = 2;

    @Column(name = "third_place_match", nullable = false)
    private boolean thirdPlaceMatch = true;

    @Column(name = "max_matches_per_player", nullable = false)
    private int maxMatchesPerPlayer = 2;

    @Column(name = "match_minutes", nullable = false)
    private int matchMinutes = 40;

    @Column(name = "rest_minutes", nullable = false)
    private int restMinutes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CompetitionSettings() {
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void update(CompetitionFormat format, int groupCount, int qualifiersPerGroup, boolean thirdPlaceMatch,
            int maxMatchesPerPlayer, int matchMinutes, int restMinutes) {
        this.format = format;
        this.groupCount = groupCount;
        this.qualifiersPerGroup = qualifiersPerGroup;
        this.thirdPlaceMatch = thirdPlaceMatch;
        this.maxMatchesPerPlayer = maxMatchesPerPlayer;
        this.matchMinutes = matchMinutes;
        this.restMinutes = restMinutes;
    }

    /** Minutes a match occupies its court, including the rest before the next one. */
    public int slotMinutes() {
        return matchMinutes + restMinutes;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public CompetitionFormat getFormat() {
        return format;
    }

    public int getGroupCount() {
        return groupCount;
    }

    public int getQualifiersPerGroup() {
        return qualifiersPerGroup;
    }

    public boolean isThirdPlaceMatch() {
        return thirdPlaceMatch;
    }

    public int getMaxMatchesPerPlayer() {
        return maxMatchesPerPlayer;
    }

    public int getMatchMinutes() {
        return matchMinutes;
    }

    public int getRestMinutes() {
        return restMinutes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
