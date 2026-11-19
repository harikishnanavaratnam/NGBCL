package lk.ngbcl.scoring.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.competition.domain.Match;
import lk.ngbcl.competition.domain.MatchGame;
import lk.ngbcl.competition.domain.MatchGameRepository;
import lk.ngbcl.competition.domain.MatchPlayerRepository;
import lk.ngbcl.competition.domain.MatchRepository;
import lk.ngbcl.competition.domain.MatchStatus;
import lk.ngbcl.competition.domain.TeamFixture;
import lk.ngbcl.competition.domain.TeamFixtureRepository;
import lk.ngbcl.scoring.domain.ScoreEvent;
import lk.ngbcl.scoring.domain.ScoreEventRepository;
import lk.ngbcl.scoring.domain.ScoringSettings;
import lk.ngbcl.scoring.service.ScoringDtos.MatchState;
import lk.ngbcl.scoring.service.ScoringRules.Tally;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The scorer's commands (SRS §4.10). Every command locks the match row, is idempotent by its
 * client event id, appends to the score history and rebuilds games from it — so undo works across
 * game boundaries and a retried tap is never counted twice.
 */
@Service
public class ScoringService {

    static final List<String> EFFECTIVE = List.of("POINT", "MANUAL", "WALKOVER", "RETIRED");

    private final MatchRepository matches;
    private final TeamFixtureRepository fixtures;
    private final MatchGameRepository games;
    private final MatchPlayerRepository lineups;
    private final ScoreEventRepository events;
    private final ScoringSettingsService settings;
    private final ScoringReadModel read;
    private final ResultService results;
    private final MatchLedger ledger;
    private final TournamentService tournament;
    private final AuditService audit;
    private final Clock clock;

    public ScoringService(MatchRepository matches, TeamFixtureRepository fixtures, MatchGameRepository games,
            MatchPlayerRepository lineups, ScoreEventRepository events, ScoringSettingsService settings,
            ScoringReadModel read, ResultService results, MatchLedger ledger, TournamentService tournament,
            AuditService audit, Clock clock) {
        this.matches = matches;
        this.fixtures = fixtures;
        this.games = games;
        this.lineups = lineups;
        this.events = events;
        this.settings = settings;
        this.read = read;
        this.results = results;
        this.ledger = ledger;
        this.tournament = tournament;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- Access --------------------------------------------------------------------------------

    static boolean hasAuthority(String a) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(g -> a.equals(g.getAuthority()));
    }

    /** Assigned scorer, or an official allowed to score any match. */
    private Match lockForScorer(UUID matchId) {
        Match m = matches.lock(matchId).orElseThrow(() -> ApiException.notFound("Match"));
        TeamFixture f = fixtures.findById(m.getFixtureId()).orElseThrow();
        if (!f.getTournamentId().equals(tournament.currentTournamentId())) {
            throw ApiException.notFound("Match");
        }
        UUID me = CurrentUser.requireId();
        if (!me.equals(m.getScorerUserId()) && !hasAuthority("MATCH_SCORE_ANY")) {
            throw ApiException.forbidden("NOT_YOUR_MATCH", "This match is assigned to another scorer.");
        }
        return m;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('MATCH_SCORE', 'MATCH_SCORE_ANY')")
    public MatchState view(UUID matchId) {
        Match m = matches.findById(matchId).orElseThrow(() -> ApiException.notFound("Match"));
        UUID me = CurrentUser.requireId();
        if (!me.equals(m.getScorerUserId()) && !hasAuthority("MATCH_SCORE_ANY")) {
            throw ApiException.forbidden("NOT_YOUR_MATCH", "This match is assigned to another scorer.");
        }
        return read.state(matchId);
    }

    // ---- Commands ------------------------------------------------------------------------------

    /** Starts the match: rules are fixed from now on; the chosen side serves first. */
    @Transactional
    @PreAuthorize("hasAnyAuthority('MATCH_SCORE', 'MATCH_SCORE_ANY')")
    public void start(UUID matchId, String firstServer, String clientEventId) {
        Match m = lockForScorer(matchId);
        if (replayed(m, clientEventId)) {
            return;
        }
        if (m.getStatus() != MatchStatus.SCHEDULED) {
            throw ApiException.conflict("ALREADY_STARTED", "This match has already started.");
        }
        side(firstServer);
        TeamFixture f = fixtures.findById(m.getFixtureId()).orElseThrow();
        if (f.getTeamAId() == null || f.getTeamBId() == null) {
            throw ApiException.conflict("TEAMS_UNKNOWN", "Both teams must be known before the match starts.");
        }
        long a = lineups.findByMatchId(m.getId()).stream().filter(p -> "A".equals(p.getSide())).count();
        long b = lineups.findByMatchId(m.getId()).stream().filter(p -> "B".equals(p.getSide())).count();
        if (a != m.getPlayersPerSide() || b != m.getPlayersPerSide()) {
            throw ApiException.conflict("LINEUP_INCOMPLETE", "Both line-ups must be set before the match starts.");
        }
        ScoringSettings s = settings.peek(f.getTournamentId());
        ScoringRules r = settings.rulesFor(m, s);
        m.setRules(r.pointsToWin(), r.winBy(), r.cap(), r.gamesToWin(), m.isRulesCustom());
        m.start(firstServer, CurrentUser.requireId(), clock.instant());
        append(m, clientEventId, "START", firstServer, null);
        rebuild(m);
        results.afterMatchChange(m, "MATCH_STARTED", true);
    }

    /** One rally to {@code side}. Completes games and the match automatically. */
    @Transactional
    @PreAuthorize("hasAnyAuthority('MATCH_SCORE', 'MATCH_SCORE_ANY')")
    public void point(UUID matchId, String side, String clientEventId) {
        Match m = lockForScorer(matchId);
        if (replayed(m, clientEventId)) {
            return;
        }
        requireLive(m);
        side(side);
        if (m.getWinnerSide() != null) {
            throw ApiException.conflict("MATCH_DECIDED", "The match is decided. Undo the last point or submit the result.");
        }
        append(m, clientEventId, "POINT", side, null);
        rebuild(m);
        results.afterMatchChange(m, "POINT", false);
    }

    /** Takes back the latest point (or the latest manual result / walkover / retirement). */
    @Transactional
    @PreAuthorize("hasAnyAuthority('MATCH_SCORE', 'MATCH_SCORE_ANY')")
    public void undo(UUID matchId, String clientEventId) {
        Match m = lockForScorer(matchId);
        if (replayed(m, clientEventId)) {
            return;
        }
        requireLive(m);
        List<ScoreEvent> all = events.findByMatchIdOrderBySeqAsc(m.getId());
        ScoreEvent last = null;
        for (ScoreEvent e : all) {
            if (!e.isVoided() && EFFECTIVE.contains(e.getType())) {
                last = e;
            }
        }
        if (last == null) {
            throw ApiException.conflict("NOTHING_TO_UNDO", "There's nothing to undo.");
        }
        last.voidIt();
        append(m, clientEventId, "UNDO", last.getSide(), "Undid " + last.getType().toLowerCase() + " #" + last.getSeq());
        rebuild(m);
        results.afterMatchChange(m, "UNDO", false);
    }

    /** Game-by-game entry (e.g. paper scoresheet). Must be a complete, valid result. */
    @Transactional
    @PreAuthorize("hasAnyAuthority('MATCH_SCORE', 'MATCH_SCORE_ANY')")
    public void manual(UUID matchId, List<int[]> gameScores, String firstServer, String clientEventId) {
        Match m = lockForScorer(matchId);
        if (replayed(m, clientEventId)) {
            return;
        }
        if (m.getStatus() == MatchStatus.SCHEDULED) {
            start(matchId, firstServer == null ? "A" : firstServer, clientEventId + ":start");
        }
        requireLive(m);
        ScoringRules r = rules(m);
        String problem = r.completeResultProblem(gameScores);
        if (problem != null) {
            throw ApiException.unprocessable("INVALID_RESULT", problem);
        }
        append(m, clientEventId, "MANUAL", null, encode(gameScores));
        rebuild(m);
        results.afterMatchChange(m, "MANUAL", false);
    }

    /** Walkover (the other side didn't play) or retirement (a side stopped mid-match). */
    @Transactional
    @PreAuthorize("hasAnyAuthority('MATCH_SCORE', 'MATCH_SCORE_ANY')")
    public void concede(UUID matchId, String type, String side, String note, String clientEventId) {
        Match m = lockForScorer(matchId);
        if (replayed(m, clientEventId)) {
            return;
        }
        ScoringSettings s = settings.peek(fixtures.findById(m.getFixtureId()).orElseThrow().getTournamentId());
        side(side);
        if ("WALKOVER".equals(type)) {
            if (!s.isAllowWalkover()) {
                throw ApiException.conflict("NOT_ALLOWED", "Walkovers are turned off in the scoring rules.");
            }
            if (m.getStatus() == MatchStatus.SCHEDULED) {
                start(matchId, "A", clientEventId + ":start");
            }
        } else if ("RETIRED".equals(type)) {
            if (!s.isAllowRetirement()) {
                throw ApiException.conflict("NOT_ALLOWED", "Retirements are turned off in the scoring rules.");
            }
        } else {
            throw ApiException.badRequest("INVALID_TYPE", "Use WALKOVER or RETIRED.");
        }
        requireLive(m);
        if (note == null || note.isBlank()) {
            throw ApiException.unprocessable("REASON_REQUIRED", "Add a short note, e.g. injury or no-show.");
        }
        // WALKOVER: side = the winner. RETIRED: side = the side that retired.
        append(m, clientEventId, type, side, note.trim().length() > 300 ? note.trim().substring(0, 300) : note.trim());
        rebuild(m);
        results.afterMatchChange(m, type, false);
    }

    /** Sends the decided result for confirmation (two-step) or makes it final (one-step). */
    @Transactional
    @PreAuthorize("hasAnyAuthority('MATCH_SCORE', 'MATCH_SCORE_ANY')")
    public void submit(UUID matchId, String clientEventId) {
        Match m = lockForScorer(matchId);
        if (replayed(m, clientEventId)) {
            return;
        }
        requireLive(m);
        if (m.getWinnerSide() == null) {
            throw ApiException.conflict("NOT_DECIDED", "The match isn't finished yet.");
        }
        Instant now = clock.instant();
        UUID me = CurrentUser.requireId();
        m.submit(me, now);
        append(m, clientEventId, "SUBMIT", m.getWinnerSide(), null);
        TeamFixture f = fixtures.findById(m.getFixtureId()).orElseThrow();
        boolean twoStep = settings.peek(f.getTournamentId()).isTwoStepApproval();
        if (!twoStep) {
            m.confirm(me, now);
        }
        matches.flush();
        audit.record(AuditEvent.of(twoStep ? "match.result.submitted" : "match.result.final", "Match", m.getId())
                .inTournament(f.getTournamentId()).withChange(null, ResultService.resultMap(m, games)));
        results.afterMatchChange(m, twoStep ? "MATCH_SUBMITTED" : "MATCH_COMPLETED", true);
    }

    // ---- Internals -----------------------------------------------------------------------------

    private ScoringRules rules(Match m) {
        return ledger.rules(m);
    }

    private void append(Match m, String clientEventId, String type, String side, String note) {
        ledger.append(m, clientEventId, type, side, note);
    }

    private void rebuild(Match m) {
        ledger.rebuild(m);
    }

    static String encode(List<int[]> gs) {
        return MatchLedger.encode(gs);
    }

    private static void side(String side) {
        if (!"A".equals(side) && !"B".equals(side)) {
            throw ApiException.badRequest("INVALID_SIDE", "Side must be A or B.");
        }
    }

    private static void requireLive(Match m) {
        switch (m.getStatus()) {
            case LIVE -> {
            }
            case SCHEDULED -> throw ApiException.conflict("NOT_STARTED", "Start the match first.");
            case SUBMITTED -> throw ApiException.conflict("SUBMITTED", "The result is waiting for confirmation.");
            case COMPLETED -> throw ApiException.conflict("COMPLETED", "The result is final. Ask an official for a correction.");
            case CANCELLED -> throw ApiException.conflict("CANCELLED", "This match was cancelled.");
        }
    }

    private boolean replayed(Match m, String clientEventId) {
        if (clientEventId == null || clientEventId.isBlank() || clientEventId.length() > 80) {
            throw ApiException.badRequest("CLIENT_EVENT_ID", "Each action needs a client event id.");
        }
        return events.findByMatchIdAndClientEventId(m.getId(), clientEventId).isPresent();
    }

    Optional<Match> find(UUID id) {
        return matches.findById(id);
    }
}
