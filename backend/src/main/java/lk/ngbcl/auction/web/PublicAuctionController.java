package lk.ngbcl.auction.web;

import lk.ngbcl.auction.service.AuctionDtos.PublicAuctionState;
import lk.ngbcl.auction.service.AuctionDtos.PublicResults;
import lk.ngbcl.auction.service.AuctionReadModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public auction views (display screen, website). Live updates arrive on
 * {@code /topic/public/auction}; this endpoint is the snapshot to start from or resync with.
 */
@RestController
@RequestMapping("/api/public/auction")
public class PublicAuctionController {

    private final AuctionReadModel read;

    public PublicAuctionController(AuctionReadModel read) {
        this.read = read;
    }

    @GetMapping("/state")
    public PublicAuctionState state() {
        return read.publicState();
    }

    @GetMapping("/results")
    public PublicResults results() {
        return read.publicResults();
    }
}
