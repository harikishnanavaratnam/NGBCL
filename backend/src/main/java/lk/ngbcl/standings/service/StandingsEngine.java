package lk.ngbcl.standings.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Pure group-table maths (no Spring, no database) so the ranking rules are easy to test.
 * Teams are ranked by table points, then by the configured tie-breakers in order; teams still
 * level are ordered by name and marked {@code tied}.
 */
public final class StandingsEngine {

    private StandingsEngine() {
    }

    public enum Tiebreaker {
        MATCHES_WON, MATCH_DIFF, GAME_DIFF, POINT_DIFF, GAMES_WON, HEAD_TO_HEAD
    }

    public record Rules(int pointsWin, int pointsDraw, int pointsLoss, List<Tiebreaker> tiebreakers) {
    }

    /** One finished tie. {@code result} is "A", "B" or "DRAW". */
    public record Tie(UUID teamA, UUID teamB, String result, int matchesA, int matchesB, int gamesA, int gamesB,
            int pointsA, int pointsB, long order) {
    }

    public static final class Row {
        public final UUID teamId;
        public final String name;
        public int played;
        public int won;
        public int drawn;
        public int lost;
        public int matchesWon;
        public int matchesLost;
        public int gamesWon;
        public int gamesLost;
        public int pointsWon;
        public int pointsLost;
        public int points;
        public int position;
        public boolean tied;
        /** Tie results in playing order: W, D or L. */
        public final List<String> form = new ArrayList<>();

        Row(UUID teamId, String name) {
            this.teamId = teamId;
            this.name = name;
        }
    }

    /** Ranks the given teams (all members of one group) from their finished ties. */
    public static List<Row> table(Map<UUID, String> teams, List<Tie> ties, Rules rules) {
        Map<UUID, Row> rows = new LinkedHashMap<>();
        teams.forEach((id, name) -> rows.put(id, new Row(id, name == null ? "" : name)));
        List<Tie> mine = ties.stream().filter(t -> rows.containsKey(t.teamA()) && rows.containsKey(t.teamB()))
                .sorted(Comparator.comparingLong(Tie::order)).toList();
        for (Tie t : mine) {
            add(rows.get(t.teamA()), t, true, rules);
            add(rows.get(t.teamB()), t, false, rules);
        }
        List<Row> ranked = blocks(new ArrayList<>(rows.values()), r -> r.points, mine, rules.tiebreakers(), 0, rules);
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).position = i + 1;
        }
        return ranked;
    }

    private static void add(Row r, Tie t, boolean sideA, Rules rules) {
        String mine = sideA ? "A" : "B";
        r.played++;
        if ("DRAW".equals(t.result())) {
            r.drawn++;
            r.points += rules.pointsDraw();
            r.form.add("D");
        } else if (mine.equals(t.result())) {
            r.won++;
            r.points += rules.pointsWin();
            r.form.add("W");
        } else {
            r.lost++;
            r.points += rules.pointsLoss();
            r.form.add("L");
        }
        r.matchesWon += sideA ? t.matchesA() : t.matchesB();
        r.matchesLost += sideA ? t.matchesB() : t.matchesA();
        r.gamesWon += sideA ? t.gamesA() : t.gamesB();
        r.gamesLost += sideA ? t.gamesB() : t.gamesA();
        r.pointsWon += sideA ? t.pointsA() : t.pointsB();
        r.pointsLost += sideA ? t.pointsB() : t.pointsA();
    }

    /**
     * Sorts by {@code key}, then splits into blocks of equal teams and applies the next tie-breaker to
     * each block. Head-to-head uses a mini table of the ties between the teams in the block.
     */
    private static List<Row> blocks(List<Row> rows, ToIntFunction<Row> key, List<Tie> ties, List<Tiebreaker> tbs,
            int next, Rules rules) {
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(key).reversed());
        List<Row> out = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            int j = i;
            int k = key.applyAsInt(sorted.get(i));
            while (j < sorted.size() && key.applyAsInt(sorted.get(j)) == k) {
                j++;
            }
            List<Row> block = sorted.subList(i, j);
            out.addAll(block.size() == 1 ? block : breakTie(new ArrayList<>(block), ties, tbs, next, rules));
            i = j;
        }
        return out;
    }

    private static List<Row> breakTie(List<Row> block, List<Tie> ties, List<Tiebreaker> tbs, int next, Rules rules) {
        if (next >= tbs.size()) {
            block.sort(Comparator.comparing((Row r) -> r.name, String.CASE_INSENSITIVE_ORDER));
            block.forEach(r -> r.tied = true);
            return block;
        }
        Tiebreaker tb = tbs.get(next);
        ToIntFunction<Row> key = switch (tb) {
            case MATCHES_WON -> r -> r.matchesWon;
            case MATCH_DIFF -> r -> r.matchesWon - r.matchesLost;
            case GAME_DIFF -> r -> r.gamesWon - r.gamesLost;
            case POINT_DIFF -> r -> r.pointsWon - r.pointsLost;
            case GAMES_WON -> r -> r.gamesWon;
            case HEAD_TO_HEAD -> headToHead(block, ties, rules)::get;
        };
        // If this tie-breaker doesn't separate anyone, move on without splitting.
        if (block.stream().mapToInt(key).distinct().count() == 1) {
            return breakTie(block, ties, tbs, next + 1, rules);
        }
        return blocks(block, key, ties, tbs, next + 1, rules);
    }

    /** Mini-table points from the ties played among the teams in the block. */
    private static Map<Row, Integer> headToHead(List<Row> block, List<Tie> ties, Rules rules) {
        Set<UUID> ids = block.stream().map(r -> r.teamId).collect(Collectors.toSet());
        Map<UUID, Integer> pts = new HashMap<>();
        for (Tie t : ties) {
            if (!ids.contains(t.teamA()) || !ids.contains(t.teamB())) {
                continue;
            }
            int a = "A".equals(t.result()) ? rules.pointsWin() : "B".equals(t.result()) ? rules.pointsLoss() : rules.pointsDraw();
            int b = "B".equals(t.result()) ? rules.pointsWin() : "A".equals(t.result()) ? rules.pointsLoss() : rules.pointsDraw();
            pts.merge(t.teamA(), a, Integer::sum);
            pts.merge(t.teamB(), b, Integer::sum);
        }
        Map<Row, Integer> out = new HashMap<>();
        block.forEach(r -> out.put(r, pts.getOrDefault(r.teamId, 0)));
        return out;
    }

    /** Parses a stored "A,B,C" list, skipping unknown names and duplicates. */
    public static List<Tiebreaker> parse(String csv) {
        List<Tiebreaker> out = new ArrayList<>();
        if (csv == null) {
            return out;
        }
        for (String s : csv.split(",")) {
            try {
                Tiebreaker tb = Tiebreaker.valueOf(s.trim());
                if (!out.contains(tb)) {
                    out.add(tb);
                }
            } catch (IllegalArgumentException ignored) {
                // unknown tie-breaker in stored settings: skip it
            }
        }
        return out;
    }
}
