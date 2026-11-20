package lk.ngbcl.standings.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.ngbcl.standings.service.StandingsDtos.CategoryLeaders;
import lk.ngbcl.standings.service.StandingsDtos.CategoryRecord;
import lk.ngbcl.standings.service.StandingsDtos.Leader;
import lk.ngbcl.standings.service.StandingsDtos.PlayerStat;
import lk.ngbcl.standings.service.StandingsDtos.StatsView;
import lk.ngbcl.standings.service.StandingsDtos.TeamStat;
import lk.ngbcl.standings.service.StandingsService.TieRow;
import lk.ngbcl.team.service.TeamService;
import lk.ngbcl.team.service.TeamService.TeamCard;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Player and team records and per-event leaders (STA-007..009), from confirmed matches in published
 * ties only. Walkovers count towards the tie and the team, not towards a player's played/won record.
 */
@Service
public class StatsService {

    static final int LEADERS = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final StandingsService standings;
    private final TeamService teams;
    private final TournamentService tournament;

    public StatsService(NamedParameterJdbcTemplate jdbc, StandingsService standings, TeamService teams,
            TournamentService tournament) {
        this.jdbc = jdbc;
        this.standings = standings;
        this.teams = teams;
        this.tournament = tournament;
    }

    private static final class P {
        String slug;
        String name;
        UUID teamId;
        int played;
        int won;
        int lost;
        int gamesWon;
        int gamesLost;
        int pointsWon;
        int pointsLost;
        int walkovers;
        final Map<String, int[]> byCat = new LinkedHashMap<>();
    }

    private static final class T {
        int played;
        int won;
        int drawn;
        int lost;
        int matchesWon;
        int matchesLost;
        int gamesWon;
        int gamesLost;
        int pointsWon;
        int pointsLost;
        final List<String> form = new ArrayList<>();
    }

    record Cat(String code, String name, int order) {
    }

    @Transactional(readOnly = true)
    public StatsView publicStats() {
        UUID t = tournament.currentTournamentId();
        Map<UUID, TeamCard> cards = teams.cards(t);
        Map<UUID, P> players = new LinkedHashMap<>();
        Map<String, Cat> cats = new LinkedHashMap<>();
        jdbc.query("""
                SELECT mp.tournament_player_id AS entry, p.slug, p.full_name, mp.team_id, mp.side, m.winner_side,
                       m.outcome, m.games_a, m.games_b, m.points_a, m.points_b,
                       c.code, c.name AS cat_name, c.display_order
                FROM match_players mp
                JOIN matches m ON m.id = mp.match_id
                JOIN team_fixtures f ON f.id = m.fixture_id
                JOIN match_categories c ON c.id = m.category_id
                JOIN tournament_players tp ON tp.id = mp.tournament_player_id
                JOIN players p ON p.id = tp.player_id
                WHERE f.tournament_id = :t AND f.published AND m.status = 'COMPLETED'
                ORDER BY c.display_order, m.confirmed_at
                """, new MapSqlParameterSource("t", t), rs -> {
            UUID entry = rs.getObject("entry", UUID.class);
            P p = players.computeIfAbsent(entry, k -> new P());
            p.slug = rs.getString("slug");
            p.name = rs.getString("full_name");
            p.teamId = rs.getObject("team_id", UUID.class);
            String code = rs.getString("code");
            cats.putIfAbsent(code, new Cat(code, rs.getString("cat_name"), rs.getInt("display_order")));
            boolean a = "A".equals(rs.getString("side"));
            boolean won = rs.getString("side").equals(rs.getString("winner_side"));
            if ("WALKOVER".equals(rs.getString("outcome"))) {
                if (won) {
                    p.walkovers++;
                }
                return;
            }
            int ga = rs.getInt("games_a");
            int gb = rs.getInt("games_b");
            int pa = rs.getInt("points_a");
            int pb = rs.getInt("points_b");
            p.played++;
            if (won) {
                p.won++;
            } else {
                p.lost++;
            }
            p.gamesWon += a ? ga : gb;
            p.gamesLost += a ? gb : ga;
            p.pointsWon += a ? pa : pb;
            p.pointsLost += a ? pb : pa;
            int[] rec = p.byCat.computeIfAbsent(code, k -> new int[4]);
            rec[0]++;
            if (won) {
                rec[1]++;
            }
            rec[2] += a ? ga : gb;
            rec[3] += a ? gb : ga;
        });

        List<PlayerStat> playerStats = players.values().stream()
                .map(p -> new PlayerStat(p.slug, p.name, StandingsService.ref(cards.get(p.teamId)), p.played, p.won,
                        p.lost, p.gamesWon, p.gamesLost, p.pointsWon, p.pointsLost, p.walkovers,
                        p.byCat.entrySet().stream().map(e -> new CategoryRecord(e.getKey(), cats.get(e.getKey()).name(),
                                e.getValue()[0], e.getValue()[1])).toList()))
                .sorted(Comparator.comparingInt(PlayerStat::won).reversed()
                        .thenComparing(Comparator.comparingInt(PlayerStat::lost))
                        .thenComparing(Comparator.comparingInt((PlayerStat s) -> s.gamesWon() - s.gamesLost()).reversed())
                        .thenComparing(PlayerStat::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<CategoryLeaders> leaders = cats.values().stream().sorted(Comparator.comparingInt(Cat::order)).map(c -> {
            List<Leader> top = players.values().stream().filter(p -> p.byCat.containsKey(c.code()))
                    .map(p -> {
                        int[] rec = p.byCat.get(c.code());
                        return new Leader(p.slug, p.name, StandingsService.ref(cards.get(p.teamId)), rec[0], rec[1],
                                rec[2] - rec[3]);
                    })
                    .filter(l -> l.played() > 0)
                    .sorted(Comparator.comparingInt(Leader::won).reversed()
                            .thenComparing(Comparator.comparingInt(Leader::played))
                            .thenComparing(Comparator.comparingInt(Leader::gameDiff).reversed())
                            .thenComparing(Leader::fullName, String.CASE_INSENSITIVE_ORDER))
                    .limit(LEADERS).toList();
            return new CategoryLeaders(c.code(), c.name(), top);
        }).filter(c -> !c.leaders().isEmpty()).toList();

        return new StatsView(playerStats, teamStats(t, cards), leaders);
    }

    private List<TeamStat> teamStats(UUID t, Map<UUID, TeamCard> cards) {
        Map<UUID, T> out = new HashMap<>();
        for (TieRow x : standings.ties(t, true)) {
            if (!x.done() || x.teamA() == null || x.teamB() == null) {
                continue;
            }
            add(out.computeIfAbsent(x.teamA(), k -> new T()), x, true);
            add(out.computeIfAbsent(x.teamB(), k -> new T()), x, false);
        }
        return out.entrySet().stream().filter(e -> cards.containsKey(e.getKey()))
                .map(e -> {
                    T s = e.getValue();
                    return new TeamStat(StandingsService.ref(cards.get(e.getKey())), s.played, s.won, s.drawn, s.lost,
                            s.matchesWon, s.matchesLost, s.gamesWon, s.gamesLost, s.pointsWon, s.pointsLost,
                            List.copyOf(s.form.subList(Math.max(0, s.form.size() - 5), s.form.size())));
                })
                .sorted(Comparator.comparingInt(TeamStat::tiesWon).reversed()
                        .thenComparing(Comparator.comparingInt(TeamStat::tiesLost))
                        .thenComparing(Comparator.comparingInt((TeamStat s) -> s.matchesWon() - s.matchesLost()).reversed())
                        .thenComparing(s -> s.team().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static void add(T s, TieRow x, boolean sideA) {
        s.played++;
        String me = sideA ? "A" : "B";
        if ("DRAW".equals(x.result())) {
            s.drawn++;
            s.form.add("D");
        } else if (me.equals(x.result())) {
            s.won++;
            s.form.add("W");
        } else {
            s.lost++;
            s.form.add("L");
        }
        s.matchesWon += sideA ? x.matchesA() : x.matchesB();
        s.matchesLost += sideA ? x.matchesB() : x.matchesA();
        s.gamesWon += sideA ? x.gamesA() : x.gamesB();
        s.gamesLost += sideA ? x.gamesB() : x.gamesA();
        s.pointsWon += sideA ? x.pointsA() : x.pointsB();
        s.pointsLost += sideA ? x.pointsB() : x.pointsA();
    }
}
