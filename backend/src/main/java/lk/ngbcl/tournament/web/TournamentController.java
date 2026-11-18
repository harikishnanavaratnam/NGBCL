package lk.ngbcl.tournament.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import lk.ngbcl.files.service.FileService;
import lk.ngbcl.tournament.domain.Tournament;
import lk.ngbcl.tournament.domain.TournamentPhase;
import lk.ngbcl.tournament.domain.TournamentSettings;
import lk.ngbcl.tournament.domain.TournamentStatus;
import lk.ngbcl.tournament.domain.WindowState;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public view of the current edition for the home page and registration forms.
 * Only publishable fields — the organiser contact is intentionally public; no personal data
 * or internal notes ever appear here (PUB-008).
 */
@RestController
public class TournamentController {

    private final TournamentService tournaments;
    private final FileService files;
    private final Clock clock;

    public TournamentController(TournamentService tournaments, FileService files, Clock clock) {
        this.tournaments = tournaments;
        this.files = files;
        this.clock = clock;
    }

    public record PublicWindow(WindowState state, Instant opensAt, Instant closesAt) {
    }

    public record PublicPrizes(Long first, Long second, Long third) {
    }

    public record PublicBranding(String logoUrl, String faviconUrl, String organiserLogoUrl, String posterUrl) {
    }

    public record PublicTournamentResponse(String slug, String name, String shortName, String edition, String tagline,
            String description, String organiser, String presenterLine, LocalDate startsOn, LocalDate endsOn,
            String venue, String city, TournamentStatus status, String timezone, String currency, TournamentPhase phase,
            PublicWindow playerRegistration, PublicWindow teamRegistration, PublicPrizes prizes, Long teamEntryFee,
            String facebookUrl, String contactEmail, String contactPhone, boolean showPlayerPrices,
            boolean showTeamBudgets, PublicBranding branding, Instant serverTime) {
    }

    @GetMapping("/api/public/tournament")
    @Transactional
    public ResponseEntity<PublicTournamentResponse> current() {
        Tournament t = tournaments.current();
        TournamentSettings s = tournaments.settingsFor(t.getId());
        Instant now = clock.instant();
        PublicBranding branding = new PublicBranding(url(s.getLogoAssetId()), url(s.getFaviconAssetId()),
                url(s.getOrganiserLogoAssetId()), url(s.getPosterAssetId()));
        var body = new PublicTournamentResponse(t.getSlug(), t.getName(), t.getShortName(), t.getEdition(),
                t.getTagline(), t.getDescription(), t.getOrganiser(), t.getPresenterLine(), t.getStartsOn(), t.getEndsOn(),
                t.getVenue(), t.getCity(), t.getStatus(), t.getTimezone(), t.getCurrency(), s.phase(now, t.getStatus()),
                new PublicWindow(s.playerWindow(now, t.getStatus()), s.getPlayerRegOpensAt(), s.getPlayerRegClosesAt()),
                new PublicWindow(s.teamWindow(now, t.getStatus()), s.getTeamRegOpensAt(), s.getTeamRegClosesAt()),
                new PublicPrizes(s.getPrizeFirst(), s.getPrizeSecond(), s.getPrizeThird()), s.getTeamEntryFee(),
                s.getFacebookUrl(), s.getContactEmail(), s.getContactPhone(), s.isShowPlayerPrices(),
                s.isShowTeamBudgets(), branding, now);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(body);
    }

    private String url(java.util.UUID assetId) {
        return files.publicUrl(assetId).orElse(null);
    }
}
