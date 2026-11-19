package lk.ngbcl.competition.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Pure fixture-shape maths (no database): round-robin pairings and knockout brackets.
 * Kept separate so it is easy to unit-test and reason about.
 */
public final class FixtureGenerator {

    private FixtureGenerator() {
    }

    public record Pairing(int round, UUID a, UUID b) {
    }

    /**
     * Circle method: every team meets every other once. With an odd number of teams one team
     * rests each round. Home/away alternates so no team is always listed first.
     */
    public static List<Pairing> roundRobin(List<UUID> teams) {
        List<UUID> list = new ArrayList<>(teams);
        if (list.size() < 2) {
            return List.of();
        }
        if (list.size() % 2 == 1) {
            list.add(null);
        }
        int n = list.size();
        List<Pairing> out = new ArrayList<>();
        for (int round = 0; round < n - 1; round++) {
            for (int i = 0; i < n / 2; i++) {
                UUID x = list.get(i);
                UUID y = list.get(n - 1 - i);
                if (x == null || y == null) {
                    continue;
                }
                boolean flip = (i == 0 && round % 2 == 1) || (i > 0 && i % 2 == 1);
                out.add(new Pairing(round + 1, flip ? y : x, flip ? x : y));
            }
            // rotate all but the first
            UUID last = list.remove(n - 1);
            list.add(1, last);
        }
        return out;
    }

    /**
     * Standard bracket order for {@code size} seeds (power of two): 1 v size, then the halves are
     * arranged so seeds 1 and 2 can only meet in the final. Returns seed numbers (1-based) in
     * slot order, two consecutive slots per first-round match.
     */
    public static List<Integer> bracketOrder(int size) {
        List<Integer> order = new ArrayList<>(List.of(1, 2));
        while (order.size() < size) {
            int n = order.size() * 2;
            List<Integer> next = new ArrayList<>();
            for (int s : order) {
                next.add(s);
                next.add(n + 1 - s);
            }
            order = next;
        }
        return order;
    }

    public static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }

    /** "Final", "Semi-finals", "Quarter-finals", "Round of 16" … by number of teams in the round. */
    public static String roundName(int teamsInRound) {
        return switch (teamsInRound) {
            case 2 -> "Final";
            case 4 -> "Semi-finals";
            case 8 -> "Quarter-finals";
            default -> "Round of " + teamsInRound;
        };
    }

    /** Short code used in "Winner of SF1" style labels. */
    public static String roundCode(int teamsInRound) {
        return switch (teamsInRound) {
            case 2 -> "F";
            case 4 -> "SF";
            case 8 -> "QF";
            default -> "R" + teamsInRound + "-";
        };
    }

    public static <T> List<T> reversed(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }
}
