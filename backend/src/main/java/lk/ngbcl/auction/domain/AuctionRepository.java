package lk.ngbcl.auction.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    Optional<Auction> findByTournamentId(UUID tournamentId);

    /** Serialises every auction command (bids, sales, queue changes) for this edition. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.tournamentId = :t")
    Optional<Auction> lockByTournamentId(@Param("t") UUID tournamentId);

    /** Creates the edition's auction row once; safe when two requests race. */
    @Modifying
    @Query(value = """
            INSERT INTO auctions (id, tournament_id, created_at, updated_at)
            VALUES (:id, :t, now(), now()) ON CONFLICT (tournament_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfMissing(@Param("id") UUID id, @Param("t") UUID tournamentId);
}
