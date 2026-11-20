package lk.ngbcl.standings.web;

import lk.ngbcl.standings.service.StandingsDtos.StandingsView;
import lk.ngbcl.standings.service.StandingsDtos.StatsView;
import lk.ngbcl.standings.service.StandingsService;
import lk.ngbcl.standings.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public tables and records — confirmed results from published ties only. */
@RestController
public class PublicStandingsController {

    private final StandingsService standings;
    private final StatsService stats;

    public PublicStandingsController(StandingsService standings, StatsService stats) {
        this.standings = standings;
        this.stats = stats;
    }

    @GetMapping("/api/public/standings")
    public StandingsView standings() {
        return standings.publicStandings();
    }

    @GetMapping("/api/public/stats")
    public StatsView stats() {
        return stats.publicStats();
    }
}
