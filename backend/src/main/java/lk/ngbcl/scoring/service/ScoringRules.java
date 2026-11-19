package lk.ngbcl.scoring.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Badminton rally-scoring rules, no database (easy to unit-test). A game is won by the first side
 * to reach {@code pointsToWin} with a lead of {@code winBy}, or by reaching {@code cap} (e.g. 30–29).
 * A match is won by the first side to win {@code gamesToWin} games (2 = best of 3).
 */
public record ScoringRules(int pointsToWin, int winBy, int cap, int gamesToWin) {

    public static final ScoringRules BWF = new ScoringRules(21, 2, 30, 2);

    /** Null if valid, else a message. */
    public String problem() {
        if (pointsToWin < 5 || pointsToWin > 50) {
            return "Points to win must be between 5 and 50.";
        }
        if (winBy < 1 || winBy > 5) {
            return "Win by must be between 1 and 5.";
        }
        if (cap < pointsToWin || cap > 99) {
            return "The point cap must be at least the points to win (and at most 99).";
        }
        if (gamesToWin < 1 || gamesToWin > 3) {
            return "Best of 1, 3 or 5.";
        }
        return null;
    }

    public int maxGames() {
        return gamesToWin * 2 - 1;
    }

    /** "A", "B" or null if the game is still going. */
    public String gameWinner(int a, int b) {
        if (a >= cap || (a >= pointsToWin && a - b >= winBy)) {
            return "A";
        }
        if (b >= cap || (b >= pointsToWin && b - a >= winBy)) {
            return "B";
        }
        return null;
    }

    /**
     * A finished game score that can actually happen: someone has won, and one rally earlier the
     * game was still going (so 21–15, 23–21 and 30–29 are fine; 25–15 or 31–29 are not).
     */
    public boolean isFinalScore(int a, int b) {
        if (a < 0 || b < 0) {
            return false;
        }
        String w = gameWinner(a, b);
        if (w == null) {
            return false;
        }
        int pa = "A".equals(w) ? a - 1 : a;
        int pb = "B".equals(w) ? b - 1 : b;
        return pa >= 0 && pb >= 0 && gameWinner(pa, pb) == null;
    }

    /** A score that can be reached during play (game not over yet, nobody past the cap). */
    public boolean isRunningScore(int a, int b) {
        return a >= 0 && b >= 0 && a <= cap && b <= cap && gameWinner(a, b) == null;
    }

    /** Game wins per side for a list of games {a, b}; null winner if the match is undecided. */
    public record Tally(int gamesA, int gamesB, int pointsA, int pointsB, String winner) {
    }

    public Tally tally(List<int[]> games) {
        int ga = 0;
        int gb = 0;
        int pa = 0;
        int pb = 0;
        for (int[] g : games) {
            pa += g[0];
            pb += g[1];
            String w = gameWinner(g[0], g[1]);
            if ("A".equals(w)) {
                ga++;
            } else if ("B".equals(w)) {
                gb++;
            }
        }
        String winner = ga >= gamesToWin ? "A" : gb >= gamesToWin ? "B" : null;
        return new Tally(ga, gb, pa, pb, winner);
    }

    /**
     * Checks a full game-by-game result: every game but the last is final, the match is decided
     * exactly at the last game, and nothing is played after that. Null if valid.
     */
    public String completeResultProblem(List<int[]> games) {
        if (games.isEmpty()) {
            return "Enter at least one game.";
        }
        if (games.size() > maxGames()) {
            return "Best of " + maxGames() + " has at most " + maxGames() + " games.";
        }
        List<int[]> sofar = new ArrayList<>();
        for (int i = 0; i < games.size(); i++) {
            int[] g = games.get(i);
            if (!isFinalScore(g[0], g[1])) {
                return "Game " + (i + 1) + " (" + g[0] + "–" + g[1] + ") isn't a finished score under these rules.";
            }
            sofar.add(g);
            String winner = tally(sofar).winner();
            if (winner != null && i < games.size() - 1) {
                return "The match was already won after game " + (i + 1) + ".";
            }
        }
        if (tally(games).winner() == null) {
            return "Nobody has won " + gamesToWin + " game" + (gamesToWin == 1 ? "" : "s") + " yet.";
        }
        return null;
    }

    /** Rally scoring: whoever won the last rally serves; a new game is served by the last game's winner. */
    public static String server(String firstServer, String lastPointSide) {
        return lastPointSide != null ? lastPointSide : firstServer;
    }
}
