package lk.ngbcl.auction.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionLotRepository extends JpaRepository<AuctionLot, UUID> {

    List<AuctionLot> findByAuctionIdOrderByRoundAscQueueOrderAsc(UUID auctionId);

    List<AuctionLot> findByAuctionIdAndState(UUID auctionId, LotState state);

    @Query("select coalesce(max(l.queueOrder), 0) from AuctionLot l where l.auctionId = :a")
    int maxOrder(@Param("a") UUID auctionId);
}
