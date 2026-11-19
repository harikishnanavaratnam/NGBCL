package lk.ngbcl.competition.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, UUID> {

    List<MatchPlayer> findByMatchIdIn(Collection<UUID> matchIds);

    List<MatchPlayer> findByMatchId(UUID matchId);

    @Modifying
    @Query("delete from MatchPlayer p where p.matchId = :m")
    void deleteByMatch(@Param("m") UUID matchId);

    @Query("select p from MatchPlayer p where p.tournamentPlayerId in :ids")
    List<MatchPlayer> findByPlayers(@Param("ids") Collection<UUID> playerIds);
}
