package lk.ngbcl.standings.web;

import java.util.List;
import lk.ngbcl.standings.service.StandingsDtos.RulesView;
import lk.ngbcl.standings.service.StandingsDtos.SeedingResult;
import lk.ngbcl.standings.service.StandingsDtos.SeedingView;
import lk.ngbcl.standings.service.StandingsDtos.StandingsView;
import lk.ngbcl.standings.service.StandingsService;
import lk.ngbcl.standings.service.StandingsService.SettingsInput;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin › Results › Standings, knockout seeding and Settings › Standings. */
@RestController
@RequestMapping("/api/admin/standings")
public class StandingsController {

    private final StandingsService standings;

    public StandingsController(StandingsService standings) {
        this.standings = standings;
    }

    public record SettingsRequest(int pointsWin, int pointsDraw, int pointsLoss, List<String> tiebreakers, Long version) {
    }

    @GetMapping
    public StandingsView standings() {
        return standings.adminStandings();
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('STANDINGS_MANAGE', 'SETTINGS_MANAGE', 'RESULT_CONFIRM', 'FIXTURE_MANAGE')")
    public RulesView settings() {
        return standings.rules();
    }

    @PutMapping("/settings")
    public RulesView saveSettings(@RequestBody SettingsRequest r) {
        return standings.update(new SettingsInput(r.pointsWin(), r.pointsDraw(), r.pointsLoss(), r.tiebreakers(), r.version()));
    }

    @GetMapping("/seeding")
    public SeedingView seeding() {
        return standings.seeding();
    }

    @PostMapping("/seeding/apply")
    public SeedingResult applySeeding() {
        return standings.applySeeding();
    }
}
