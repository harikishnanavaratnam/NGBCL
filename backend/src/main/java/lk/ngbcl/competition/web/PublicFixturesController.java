package lk.ngbcl.competition.web;

import lk.ngbcl.competition.service.CompetitionDtos.PublicFixtures;
import lk.ngbcl.competition.service.CompetitionReadModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Published fixtures and the group draw (FIX-010). Unpublished drafts never appear here. */
@RestController
public class PublicFixturesController {

    private final CompetitionReadModel read;

    public PublicFixturesController(CompetitionReadModel read) {
        this.read = read;
    }

    @GetMapping("/api/public/fixtures")
    public PublicFixtures fixtures() {
        return read.publicFixtures();
    }
}
