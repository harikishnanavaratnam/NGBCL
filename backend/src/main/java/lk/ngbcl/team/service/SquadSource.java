package lk.ngbcl.team.service;

import java.util.List;
import java.util.UUID;

/** Supplied by the auction module: a team's current squad for its public page (ROS-003). */
public interface SquadSource {

    record SquadMember(String slug, String fullName, String categoryName, String photoUrl, Long price,
            Integer lotNumber) {
    }

    List<SquadMember> squadOf(UUID teamId);
}
