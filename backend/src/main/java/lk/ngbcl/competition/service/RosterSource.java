package lk.ngbcl.competition.service;

import java.util.List;
import java.util.UUID;

/**
 * Supplied by the auction module: a team's current squad, for line-up pickers and the FIX-008
 * check that a player belongs to the team they play for.
 */
public interface RosterSource {

    record RosterPlayer(UUID entryId, String slug, String fullName, String gender, String categoryName,
            String photoUrl) {
    }

    List<RosterPlayer> roster(UUID teamId);
}
