package lk.ngbcl.auction.service;

import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.auction.domain.Auction;

/** Audit entries for auction commands, tied to the auction and its edition. */
final class AuctionEventLog {

    private AuctionEventLog() {
    }

    static AuditEvent of(String action, Auction a) {
        return AuditEvent.of(action, "Auction", a.getId()).inTournament(a.getTournamentId());
    }
}
