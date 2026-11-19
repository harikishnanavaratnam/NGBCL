package lk.ngbcl.scoring.web;

import java.time.Clock;
import lk.ngbcl.scoring.service.ScoringDtos.PublicLive;
import lk.ngbcl.scoring.service.ScoringReadModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live scores for the public site: matches in play, recent results and what's next, from
 * published fixtures only. Updates arrive on {@code /topic/public/match/{id}} and {@code /topic/public/live}.
 */
@RestController
public class PublicLiveController {

    private final ScoringReadModel read;
    private final Clock clock;

    public PublicLiveController(ScoringReadModel read, Clock clock) {
        this.read = read;
        this.clock = clock;
    }

    @GetMapping("/api/public/live")
    public PublicLive live() {
        return read.publicLive(clock.instant());
    }
}
