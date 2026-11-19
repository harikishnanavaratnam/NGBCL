package lk.ngbcl.scoring.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.competition.domain.Match;
import lk.ngbcl.scoring.domain.ScoringSettings;
import lk.ngbcl.scoring.domain.ScoringSettingsRepository;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.FieldErrors;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Settings › Scoring rules: defaults for every match, walkover/retirement and the approval mode. */
@Service
public class ScoringSettingsService {

    private final ScoringSettingsRepository settings;
    private final TournamentService tournament;
    private final AuditService audit;

    public ScoringSettingsService(ScoringSettingsRepository settings, TournamentService tournament, AuditService audit) {
        this.settings = settings;
        this.tournament = tournament;
        this.audit = audit;
    }

    public record SettingsView(int pointsToWin, int winBy, int pointCap, int gamesToWin, boolean allowWalkover,
            boolean allowRetirement, boolean twoStepApproval, Long version) {
    }

    public record SettingsInput(int pointsToWin, int winBy, int pointCap, int gamesToWin, boolean allowWalkover,
            boolean allowRetirement, boolean twoStepApproval, Long version) {
    }

    /** Saved settings or the BWF defaults (21 · win by 2 · cap 30 · best of 3, two-step approval). */
    @Transactional(readOnly = true)
    public ScoringSettings peek(UUID t) {
        return settings.findById(t).orElseGet(() -> new ScoringSettings(t));
    }

    @Transactional(readOnly = true)
    public SettingsView get() {
        return view(peek(tournament.currentTournamentId()));
    }

    static SettingsView view(ScoringSettings s) {
        return new SettingsView(s.getPointsToWin(), s.getWinBy(), s.getPointCap(), s.getGamesToWin(), s.isAllowWalkover(),
                s.isAllowRetirement(), s.isTwoStepApproval(), s.getVersion());
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE', 'FIXTURE_MANAGE')")
    public SettingsView update(SettingsInput in) {
        UUID t = tournament.currentTournamentId();
        ScoringSettings s = settings.findById(t).orElseGet(() -> settings.saveAndFlush(new ScoringSettings(t)));
        if (in.version() != null && s.getVersion() != null && !Objects.equals(in.version(), s.getVersion())) {
            throw ApiException.conflict("STALE_DATA", "Someone else saved the scoring rules. Reload to see their changes.");
        }
        String problem = new ScoringRules(in.pointsToWin(), in.winBy(), in.pointCap(), in.gamesToWin()).problem();
        if (problem != null) {
            String field = problem.startsWith("Points") ? "pointsToWin" : problem.startsWith("Win by") ? "winBy"
                    : problem.startsWith("The point cap") ? "pointCap" : "gamesToWin";
            new FieldErrors().add(field, problem).throwIfAny();
        }
        Map<String, Object> before = snapshot(s);
        s.update(in.pointsToWin(), in.winBy(), in.pointCap(), in.gamesToWin(), in.allowWalkover(), in.allowRetirement(),
                in.twoStepApproval());
        settings.flush();
        audit.record(AuditEvent.of("scoring.settings.updated", "ScoringSettings", t).inTournament(t)
                .withChange(before, snapshot(s)));
        return view(s);
    }

    private static Map<String, Object> snapshot(ScoringSettings s) {
        Map<String, Object> m = new HashMap<>();
        m.put("pointsToWin", s.getPointsToWin());
        m.put("winBy", s.getWinBy());
        m.put("pointCap", s.getPointCap());
        m.put("gamesToWin", s.getGamesToWin());
        m.put("allowWalkover", s.isAllowWalkover());
        m.put("allowRetirement", s.isAllowRetirement());
        m.put("twoStepApproval", s.isTwoStepApproval());
        return m;
    }

    /** The rules a match plays under: its own if set, else the edition defaults. */
    public ScoringRules rulesFor(Match m, ScoringSettings s) {
        if (m.getPointsToWin() != null) {
            return new ScoringRules(m.getPointsToWin(), m.getWinBy(), m.getPointCap(), m.getGamesToWin());
        }
        return new ScoringRules(s.getPointsToWin(), s.getWinBy(), s.getPointCap(), s.getGamesToWin());
    }
}
