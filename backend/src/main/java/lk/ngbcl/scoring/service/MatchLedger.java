package lk.ngbcl.scoring.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lk.ngbcl.competition.domain.Match;
import lk.ngbcl.competition.domain.MatchGame;
import lk.ngbcl.competition.domain.MatchGameRepository;
import lk.ngbcl.competition.domain.MatchRepository;
import lk.ngbcl.competition.domain.MatchStatus;
import lk.ngbcl.competition.domain.TeamFixture;
import lk.ngbcl.competition.domain.TeamFixtureRepository;
import lk.ngbcl.scoring.domain.ScoreEvent;
import lk.ngbcl.scoring.domain.ScoreEventRepository;
import lk.ngbcl.scoring.service.ScoringRules.Tally;
import lk.ngbcl.security.CurrentUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The score history of a match and the games derived from it. Callers hold the match lock.
 */
@Component
public class MatchLedger {

    private final MatchRepository matches;
    private final TeamFixtureRepository fixtures;
    private final MatchGameRepository games;
    private final ScoreEventRepository events;
    private final ScoringSettingsService settings;
    private final Clock clock;

    public MatchLedger(MatchRepository matches, TeamFixtureRepository fixtures, MatchGameRepository games,
            ScoreEventRepository events, ScoringSettingsService settings, Clock clock) {
        this.matches = matches;
        this.fixtures = fixtures;
        this.games = games;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
    }

    public ScoringRules rules(Match m) {
        TeamFixture f = fixtures.findById(m.getFixtureId()).orElseThrow();
        return settings.rulesFor(m, settings.peek(f.getTournamentId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Match m, String clientEventId, String type, String side, String note) {
        int seq = events.maxSeq(m.getId()) + 1;
        events.save(new ScoreEvent(m.getId(), seq, clientEventId, type, side, null, null, null, note,
                CurrentUser.get().map(CurrentUser.Actor::id).orElse(null), clock.instant()));
        events.flush();
    }

    /**
     * Rebuilds games and totals from the non-voided score history: points are replayed rally by
     * rally; a manual result replaces the games; walkover/retirement decide the winner.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void rebuild(Match m) {
        ScoringRules r = rules(m);
        List<int[]> gs = new ArrayList<>();
        String forcedWinner = null;
        String outcome = null;
        String note = null;
        for (ScoreEvent e : events.findByMatchIdOrderBySeqAsc(m.getId())) {
            if (e.isVoided()) {
                continue;
            }
            switch (e.getType()) {
                case "POINT" -> {
                    if (gs.isEmpty() || r.gameWinner(gs.get(gs.size() - 1)[0], gs.get(gs.size() - 1)[1]) != null) {
                        gs.add(new int[] {0, 0});
                    }
                    int[] g = gs.get(gs.size() - 1);
                    g["A".equals(e.getSide()) ? 0 : 1]++;
                }
                case "MANUAL" -> {
                    gs = decode(e.getNote());
                    forcedWinner = null;
                    outcome = null;
                }
                case "WALKOVER" -> {
                    forcedWinner = e.getSide();
                    outcome = "WALKOVER";
                    note = e.getNote();
                }
                case "RETIRED" -> {
                    forcedWinner = "A".equals(e.getSide()) ? "B" : "A";
                    outcome = "RETIRED";
                    note = e.getNote();
                }
                default -> {
                }
            }
        }
        // After a decided match, the next game shouldn't be opened yet.
        Tally tally = r.tally(gs);
        if (tally.winner() == null && forcedWinner == null && m.getStatus() == MatchStatus.LIVE
                && (gs.isEmpty() || r.gameWinner(gs.get(gs.size() - 1)[0], gs.get(gs.size() - 1)[1]) != null)) {
            gs.add(new int[] {0, 0}); // next game ready to play
        }
        tally = r.tally(gs);
        String winner = forcedWinner != null ? forcedWinner : tally.winner();
        String out = forcedWinner != null ? outcome : tally.winner() != null ? "NORMAL" : null;
        games.deleteByMatch(m.getId());
        games.flush();
        Instant now = clock.instant();
        for (int i = 0; i < gs.size(); i++) {
            int[] g = gs.get(i);
            String w = r.gameWinner(g[0], g[1]);
            games.save(new MatchGame(m.getId(), i + 1, g[0], g[1], w, w == null ? null : now));
        }
        m.tally(tally.gamesA(), tally.gamesB(), tally.pointsA(), tally.pointsB(), winner, out);
        m.setOutcomeNote(forcedWinner != null ? note : null);
        m.bump();
        games.flush();
        matches.flush();
    }

    static String encode(List<int[]> gs) {
        StringBuilder sb = new StringBuilder();
        for (int[] g : gs) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(g[0]).append('-').append(g[1]);
        }
        return sb.toString();
    }

    static List<int[]> decode(String s) {
        List<int[]> out = new ArrayList<>();
        if (s == null || s.isBlank()) {
            return out;
        }
        for (String part : s.split(",")) {
            String[] ab = part.split("-");
            out.add(new int[] {Integer.parseInt(ab[0].trim()), Integer.parseInt(ab[1].trim())});
        }
        return out;
    }

}
