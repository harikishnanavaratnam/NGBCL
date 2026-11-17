package lk.ngbcl.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import lk.ngbcl.identity.service.HomeSurface;
import lk.ngbcl.security.Permission;
import org.junit.jupiter.api.Test;

class HomeSurfaceTest {

    @Test
    void auctioneerLandsOnConsoleScorerOnScoringAdminsOnAdmin() {
        assertThat(HomeSurface.of(EnumSet.of(Permission.TOURNAMENT_VIEW, Permission.AUCTION_CONTROL)))
                .isEqualTo(HomeSurface.AUCTION_CONSOLE);
        assertThat(HomeSurface.of(EnumSet.of(Permission.TOURNAMENT_VIEW, Permission.MATCH_SCORE)))
                .isEqualTo(HomeSurface.SCORER);
        assertThat(HomeSurface.of(EnumSet.of(Permission.REGISTRATION_REVIEW))).isEqualTo(HomeSurface.ADMIN);
    }
}
