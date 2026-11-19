package lk.ngbcl.player.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Supplied by the auction module so the player module can show a player's team and auction
 * price without depending on it. Keys are tournament-player ids.
 */
public interface PlayerTeamSource {

    record PlayerTeam(String teamSlug, String teamName, Short teamColorSlot, Long price, Integer lotNumber) {
    }

    Map<UUID, PlayerTeam> teamsOf(Collection<UUID> tournamentPlayerIds);
}
