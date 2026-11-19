package lk.ngbcl.auction.service;

import lk.ngbcl.auction.domain.AuctionLot;
import lk.ngbcl.auction.domain.AuctionSettings;
import lk.ngbcl.auction.domain.TeamBudget;

/** The bidding arithmetic, shared by the engine (enforcement) and the views (what the console shows). */
final class AuctionRules {

    private AuctionRules() {
    }

    /** Lowest acceptable next bid: the base price for the first bid, then current + increment (AUC-012). */
    static long nextMinimum(AuctionLot lot, AuctionSettings s) {
        return lot.getCurrentAmount() == null ? lot.getBasePrice() : lot.getCurrentAmount() + s.getMinIncrement();
    }

    /**
     * Money a team must keep back so it can still reach the minimum squad at the default base price
     * (AUC-008). Zero when no minimum squad is configured.
     */
    static long reserve(TeamBudget b, AuctionSettings s) {
        if (s.getMinSquadSize() == null) {
            return 0;
        }
        int stillNeededAfterThis = s.getMinSquadSize() - b.getSquadCount() - 1;
        return Math.max(0, stillNeededAfterThis) * s.getDefaultBasePrice();
    }

    /** Highest bid this team may place right now. */
    static long maxBid(TeamBudget b, AuctionSettings s) {
        return Math.max(0, b.getRemaining() - reserve(b, s));
    }

    /** Why the team can't bid on this lot, or null if it can. */
    static String blockedReason(TeamBudget b, AuctionSettings s, AuctionLot openLot) {
        if (b.getSquadCount() >= s.getMaxSquadSize()) {
            return "SQUAD_FULL";
        }
        if (openLot == null) {
            return null;
        }
        if (b.getTeamId().equals(openLot.getLeadingTeamId())) {
            return "LEADING";
        }
        long min = nextMinimum(openLot, s);
        if (b.getRemaining() < min) {
            return "BUDGET";
        }
        if (maxBid(b, s) < min) {
            return "RESERVE";
        }
        return null;
    }
}
