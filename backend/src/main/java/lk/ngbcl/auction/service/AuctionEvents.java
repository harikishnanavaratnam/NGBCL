package lk.ngbcl.auction.service;

import java.time.Clock;
import lk.ngbcl.auction.domain.Auction;
import lk.ngbcl.realtime.RealtimePublisher;
import lk.ngbcl.shared.site.SiteRevalidator;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Bumps the auction's state version and broadcasts the new public state after commit
 * (AUC-033). The console listens to the same topic and refetches its staff snapshot.
 */
@Component
public class AuctionEvents {

    public static final String TOPIC = "/topic/public/auction";

    private final RealtimePublisher realtime;
    private final AuctionReadModel readModel;
    private final SiteRevalidator site;
    private final Clock clock;

    public AuctionEvents(RealtimePublisher realtime, @Lazy AuctionReadModel readModel, SiteRevalidator site, Clock clock) {
        this.realtime = realtime;
        this.readModel = readModel;
        this.site = site;
        this.clock = clock;
    }

    public void publish(Auction auction, String event) {
        long version = auction.bump(event, clock.instant());
        realtime.publish(TOPIC, version, event, readModel.publicState(auction));
        if (event.startsWith("LOT_SOLD") || event.startsWith("CORRECTION") || event.equals("ENDED")) {
            site.touch(SiteRevalidator.TEAMS, SiteRevalidator.PLAYERS);
        }
    }
}
