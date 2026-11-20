package lk.ngbcl.scoring.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.competition.domain.FixtureStatus;
import lk.ngbcl.competition.domain.Match;
import lk.ngbcl.competition.domain.MatchGameRepository;
import lk.ngbcl.competition.domain.MatchRepository;
import lk.ngbcl.competition.domain.MatchStatus;
import lk.ngbcl.competition.domain.TeamFixture;
import lk.ngbcl.competition.domain.TeamFixtureRepository;
import lk.ngbcl.competition.service.CompetitionReadModel;
import lk.ngbcl.realtime.RealtimePublisher;
import lk.ngbcl.security.CurrentUser;
import lk.ngbcl.shared.site.SiteRevalidator;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Officials' side of results (two-step approval, return to scorer, audited corrections, per-match
 * rules) and everything that follows a match change: the tie score, the tie result, knockout
 * progression, the public copy of the fixture and the live broadcast.
 */
@Service
public class ResultService {

    public static final String LIVE_TOPIC = "/topic/public/live";

    public static String matchTopic(UUID matchId) {
        return "/topic/public/match/" + matchId;
    }

    private final MatchRepository matches;
    private final TeamFixtureRepository fixtures;
    private final MatchGameRepository games;
    private final MatchLedger ledger;
    private final ScoringReadModel read;
    private final CompetitionReadModel competition;
    private final RealtimePublisher realtime;
    private final SiteRevalidator site;
    private final TournamentService tournament;
    private final AuditService audit;
    private final Clock clock;
    private final EntityManager em;

    public ResultService(MatchRepository matches, TeamFixtureRepository fixtures, MatchGameRepository games,
            MatchLedger ledger, @Lazy ScoringReadModel read,
            CompetitionReadModel competition, RealtimePublisher realtime, SiteRevalidator site,
            TournamentService tournament, AuditService audit, Clock clock, EntityManager em) {
        this.matches = matches;
        this.fixtures = fixtures;
        this.games = games;
        this.ledger = ledger;
        this.read = read;
        this.competition = competition;
        this.realtime = realtime;
        this.site = site;
        this.tournament = tournament;
        this.audit = audit;
        this.clock = clock;
        this.em = em;
    }

    // ---- Officials -----------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('RESULT_CONFIRM')")
    public void confirm(UUID matchId) {
        Match m = lock(matchId);
        if (m.getStatus() != MatchStatus.SUBMITTED) {
            throw ApiException.conflict("NOT_SUBMITTED", "Only submitted results can be confirmed.");
        }
        m.confirm(CurrentUser.requireId(), clock.instant());
        ledger.append(m, "confirm-" + UUID.randomUUID(), "CONFIRM", m.getWinnerSide(), null);
        m.bump();
        matches.flush();
        audit.record(AuditEvent.of("match.result.confirmed", "Match", m.getId()).inTournament(tournamentOf(m))
                .withChange(null, resultMap(m, games)));
        afterMatchChange(m, "MATCH_COMPLETED", true);
    }

    @Transactional
    @PreAuthorize("hasAuthority('RESULT_CONFIRM')")
    public void returnToScorer(UUID matchId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.unprocessable("REASON_REQUIRED", "Tell the scorer what to check.");
        }
        Match m = lock(matchId);
        if (m.getStatus() != MatchStatus.SUBMITTED) {
            throw ApiException.conflict("NOT_SUBMITTED", "Only submitted results can be returned.");
        }
        String why = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim();
        m.returnToScorer(why);
        ledger.append(m, "return-" + UUID.randomUUID(), "RETURN", null, why);
        m.bump();
        matches.flush();
        audit.record(AuditEvent.of("match.result.returned", "Match", m.getId()).inTournament(tournamentOf(m))
                .withReason(why));
        afterMatchChange(m, "MATCH_RETURNED", true);
    }

    /**
     * Audited correction of a submitted or final result. {@code outcome} NORMAL needs a complete,
     * valid result; WALKOVER/RETIRED take the winner and the games as played.
     */
    @Transactional
    @PreAuthorize("hasAuthority('RESULT_CORRECT')")
    public void correct(UUID matchId, List<int[]> gameScores, String outcome, String winnerSide, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.unprocessable("REASON_REQUIRED", "Give a reason — corrections are audited.");
        }
        Match m = lock(matchId);
        if (m.getStatus() != MatchStatus.SUBMITTED && m.getStatus() != MatchStatus.COMPLETED) {
            throw ApiException.conflict("NOT_FINISHED", "Only finished results can be corrected. Use the scorer app while it's live.");
        }
        ScoringRules r = ledger.rules(m);
        String out = outcome == null ? "NORMAL" : outcome;
        if ("NORMAL".equals(out)) {
            String problem = r.completeResultProblem(gameScores);
            if (problem != null) {
                throw ApiException.unprocessable("INVALID_RESULT", problem);
            }
        } else if ("WALKOVER".equals(out) || "RETIRED".equals(out)) {
            if (!"A".equals(winnerSide) && !"B".equals(winnerSide)) {
                throw ApiException.unprocessable("INVALID_RESULT", "Choose the winning side.");
            }
            for (int[] g : gameScores) {
                if (g[0] < 0 || g[1] < 0 || g[0] > r.cap() || g[1] > r.cap()) {
                    throw ApiException.unprocessable("INVALID_RESULT", "Game scores are out of range.");
                }
            }
        } else {
            throw ApiException.badRequest("INVALID_OUTCOME", "Use NORMAL, WALKOVER or RETIRED.");
        }
        Map<String, Object> before = resultMap(m, games);
        String oldWinner = m.getWinnerSide();
        String why = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim();
        ledger.append(m, "correct-" + UUID.randomUUID(), "CORRECTION", null, why);
        ledger.append(m, "correct-games-" + UUID.randomUUID(), "MANUAL", null, MatchLedger.encode(gameScores));
        if ("WALKOVER".equals(out)) {
            ledger.append(m, "correct-wo-" + UUID.randomUUID(), "WALKOVER", winnerSide, why);
        } else if ("RETIRED".equals(out)) {
            ledger.append(m, "correct-ret-" + UUID.randomUUID(), "RETIRED", "A".equals(winnerSide) ? "B" : "A", why);
        }
        ledger.rebuild(m);
        if (!Objects.equals(oldWinner, m.getWinnerSide())) {
            requireNextRoundNotStarted(m);
        }
        if (m.getStatus() == MatchStatus.SUBMITTED) {
            m.confirm(CurrentUser.requireId(), clock.instant());
        }
        matches.flush();
        audit.record(AuditEvent.of("match.result.corrected", "Match", m.getId()).inTournament(tournamentOf(m))
                .withChange(before, resultMap(m, games)).withReason(why));
        afterMatchChange(m, "MATCH_CORRECTED", true);
    }

    /** Organisers may give one match its own rules until it starts (e.g. a single game to 15). */
    @Transactional
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public void setMatchRules(UUID matchId, ScoringRules rules) {
        Match m = lock(matchId);
        if (m.getStatus() != MatchStatus.SCHEDULED) {
            throw ApiException.conflict("MATCH_STARTED", "Rules can only change before the match starts.");
        }
        Map<String, Object> before = rulesMap(m);
        if (rules == null) {
            m.clearRules();
        } else {
            String problem = rules.problem();
            if (problem != null) {
                throw ApiException.unprocessable("INVALID_RULES", problem);
            }
            m.setRules(rules.pointsToWin(), rules.winBy(), rules.cap(), rules.gamesToWin(), true);
        }
        matches.flush();
        audit.record(AuditEvent.of("match.rules.changed", "Match", m.getId()).inTournament(tournamentOf(m))
                .withChange(before, rulesMap(m)));
    }

    // ---- After every change --------------------------------------------------------------------

    /**
     * Recomputes the tie, moves knockout winners on, refreshes public copies (on status changes)
     * and broadcasts the live score.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void afterMatchChange(Match m, String event, boolean statusChanged) {
        TeamFixture f = lockFixture(m.getFixtureId());
        String before = f.getResult();
        FixtureStatus beforeStatus = f.getStatus();
        recompute(f);
        boolean fixtureChanged = !Objects.equals(before, f.getResult()) || beforeStatus != f.getStatus();
        if (fixtureChanged) {
            progress(f);
        }
        if (f.isPublished()) {
            if (statusChanged || fixtureChanged) {
                refreeze(f);
            }
            realtime.publish(matchTopic(m.getId()), m.getStateVersion(), event, read.publicMatch(m));
            if (statusChanged || fixtureChanged) {
                realtime.publish(LIVE_TOPIC, clock.millis(), event, Map.of("matchId", m.getId().toString()));
            }
        }
    }

    private void recompute(TeamFixture f) {
        List<Match> ms = matches.findByFixtureIdOrderByMatchOrderAsc(f.getId());
        int wonA = 0;
        int wonB = 0;
        int gamesDiff = 0;
        int pointsDiff = 0;
        boolean allFinal = !ms.isEmpty();
        boolean anyStarted = false;
        for (Match x : ms) {
            if (x.getStatus() != MatchStatus.SCHEDULED) {
                anyStarted = true;
            }
            if (x.getStatus() == MatchStatus.COMPLETED) {
                if ("A".equals(x.getWinnerSide())) {
                    wonA++;
                } else if ("B".equals(x.getWinnerSide())) {
                    wonB++;
                }
                gamesDiff += x.getGamesA() - x.getGamesB();
                pointsDiff += x.getPointsA() - x.getPointsB();
            } else if (x.getStatus() != MatchStatus.CANCELLED) {
                allFinal = false;
            }
        }
        String result = null;
        UUID winner = null;
        FixtureStatus status;
        if (allFinal) {
            // Tie decided by matches won, then game difference, then point difference.
            int cmp = wonA != wonB ? Integer.compare(wonA, wonB)
                    : gamesDiff != 0 ? Integer.signum(gamesDiff) : Integer.signum(pointsDiff);
            result = cmp > 0 ? "A" : cmp < 0 ? "B" : "DRAW";
            winner = "A".equals(result) ? f.getTeamAId() : "B".equals(result) ? f.getTeamBId() : null;
            status = FixtureStatus.COMPLETED;
        } else if (anyStarted) {
            status = FixtureStatus.LIVE;
        } else {
            status = f.isPublished() ? FixtureStatus.SCHEDULED : FixtureStatus.DRAFT;
        }
        f.setScore(wonA, wonB, result, winner, status);
        fixtures.flush();
    }

    /** Knockout: winner (and semi-final loser) move into their next fixture if it hasn't started. */
    private void progress(TeamFixture f) {
        UUID winner = f.getWinnerTeamId();
        UUID loser = winner == null ? null : winner.equals(f.getTeamAId()) ? f.getTeamBId() : f.getTeamAId();
        place(f.getWinnerToFixtureId(), f.getWinnerToSide(), winner);
        place(f.getLoserToFixtureId(), f.getLoserToSide(), loser);
    }

    private void place(UUID targetId, String side, UUID team) {
        if (targetId == null || side == null) {
            return;
        }
        TeamFixture target = fixtures.existsById(targetId) ? lockFixture(targetId) : null;
        if (target == null || target.isLocked()) {
            return;
        }
        UUID current = "A".equals(side) ? target.getTeamAId() : target.getTeamBId();
        if (Objects.equals(current, team)) {
            return;
        }
        target.setTeam(side, team);
        fixtures.flush();
        if (target.isPublished()) {
            refreeze(target);
        }
    }

    private void requireNextRoundNotStarted(Match m) {
        TeamFixture f = fixtures.findById(m.getFixtureId()).orElseThrow();
        for (UUID next : new UUID[] {f.getWinnerToFixtureId(), f.getLoserToFixtureId()}) {
            if (next != null && fixtures.findById(next).map(TeamFixture::isLocked).orElse(false)) {
                throw ApiException.conflict("NEXT_ROUND_STARTED",
                        "The next knockout tie has already started, so the winner can't change.");
            }
        }
    }

    private void refreeze(TeamFixture f) {
        f.freeze(competition.snapshot(f));
        fixtures.flush();
        site.touch(SiteRevalidator.FIXTURES, SiteRevalidator.TEAMS, SiteRevalidator.PLAYERS, SiteRevalidator.STANDINGS);
    }

    // ---- Helpers -------------------------------------------------------------------------------

    private Match lock(UUID matchId) {
        Match m = matches.lock(matchId).orElseThrow(() -> ApiException.notFound("Match"));
        if (!tournamentOf(m).equals(tournament.currentTournamentId())) {
            throw ApiException.notFound("Match");
        }
        return m;
    }

    /** Row lock with fresh state (a copy loaded earlier in this transaction is refreshed). */
    private TeamFixture lockFixture(UUID id) {
        TeamFixture f = fixtures.findById(id).orElseThrow();
        em.flush();
        em.refresh(f, LockModeType.PESSIMISTIC_WRITE);
        return f;
    }

    private UUID tournamentOf(Match m) {
        return fixtures.findById(m.getFixtureId()).map(TeamFixture::getTournamentId).orElseThrow();
    }

    static Map<String, Object> resultMap(Match m, MatchGameRepository games) {
        Map<String, Object> r = new HashMap<>();
        r.put("winnerSide", m.getWinnerSide());
        r.put("outcome", m.getOutcome());
        r.put("games", games.findByMatchIdOrderByGameNumberAsc(m.getId()).stream()
                .map(g -> g.getScoreA() + "-" + g.getScoreB()).toList());
        return r;
    }

    private static Map<String, Object> rulesMap(Match m) {
        Map<String, Object> r = new HashMap<>();
        r.put("pointsToWin", m.getPointsToWin());
        r.put("winBy", m.getWinBy());
        r.put("pointCap", m.getPointCap());
        r.put("gamesToWin", m.getGamesToWin());
        return r;
    }
}
