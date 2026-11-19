package lk.ngbcl.scoring.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.scoring.service.ResultService;
import lk.ngbcl.scoring.service.ScoringDtos.MatchState;
import lk.ngbcl.scoring.service.ScoringReadModel;
import lk.ngbcl.scoring.service.ScoringRules;
import lk.ngbcl.scoring.service.ScoringSettingsService;
import lk.ngbcl.scoring.service.ScoringSettingsService.SettingsInput;
import lk.ngbcl.scoring.service.ScoringSettingsService.SettingsView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Officials: confirmation queue, return to scorer, corrections; scoring rules and per-match rules. */
@RestController
public class ResultsController {

    private final ResultService results;
    private final ScoringReadModel read;
    private final ScoringSettingsService settings;

    public ResultsController(ResultService results, ScoringReadModel read, ScoringSettingsService settings) {
        this.results = results;
        this.read = read;
        this.settings = settings;
    }

    public record ReasonRequest(@Size(max = 300) String reason) {
    }

    public record CorrectRequest(@NotNull List<List<Integer>> games, String outcome, String winnerSide,
            @Size(max = 300) String reason) {
    }

    public record RulesRequest(Integer pointsToWin, Integer winBy, Integer pointCap, Integer gamesToWin, boolean reset) {
    }

    public record SettingsRequest(int pointsToWin, int winBy, int pointCap, int gamesToWin, boolean allowWalkover,
            boolean allowRetirement, boolean twoStepApproval, Long version) {
    }

    @GetMapping("/api/admin/results/pending")
    @PreAuthorize("hasAnyAuthority('RESULT_CONFIRM', 'RESULT_CORRECT')")
    public List<MatchState> pending() {
        return read.pending();
    }

    @GetMapping("/api/admin/results/recent")
    @PreAuthorize("hasAnyAuthority('RESULT_CONFIRM', 'RESULT_CORRECT')")
    public List<MatchState> recent() {
        return read.recentCompleted(50);
    }

    @PostMapping("/api/admin/results/matches/{id}/confirm")
    public MatchState confirm(@PathVariable UUID id) {
        results.confirm(id);
        return read.state(id);
    }

    @PostMapping("/api/admin/results/matches/{id}/return")
    public MatchState returnToScorer(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        results.returnToScorer(id, r.reason());
        return read.state(id);
    }

    @PostMapping("/api/admin/results/matches/{id}/correct")
    public MatchState correct(@PathVariable UUID id, @Valid @RequestBody CorrectRequest r) {
        results.correct(id, ScorerController.games(r.games()), r.outcome(), r.winnerSide(), r.reason());
        return read.state(id);
    }

    @PutMapping("/api/admin/competition/matches/{id}/rules")
    public MatchState rules(@PathVariable UUID id, @RequestBody RulesRequest r) {
        ScoringRules rules = r.reset() || r.pointsToWin() == null ? null
                : new ScoringRules(r.pointsToWin(), r.winBy() == null ? 2 : r.winBy(),
                        r.pointCap() == null ? r.pointsToWin() : r.pointCap(), r.gamesToWin() == null ? 2 : r.gamesToWin());
        results.setMatchRules(id, rules);
        return read.state(id);
    }

    @GetMapping("/api/admin/scoring/settings")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE', 'FIXTURE_MANAGE', 'RESULT_CONFIRM')")
    public SettingsView getSettings() {
        return settings.get();
    }

    @PutMapping("/api/admin/scoring/settings")
    public SettingsView saveSettings(@RequestBody SettingsRequest r) {
        return settings.update(new SettingsInput(r.pointsToWin(), r.winBy(), r.pointCap(), r.gamesToWin(),
                r.allowWalkover(), r.allowRetirement(), r.twoStepApproval(), r.version()));
    }
}
