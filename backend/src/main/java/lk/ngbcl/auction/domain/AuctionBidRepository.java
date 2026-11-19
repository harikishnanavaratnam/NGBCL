package lk.ngbcl.auction.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, UUID> {

    Optional<AuctionBid> findByIdempotencyKey(String key);

    List<AuctionBid> findByLotIdAndRoundOrderByBidNumberDesc(UUID lotId, int round);

    List<AuctionBid> findByLotIdAndRoundAndStatusOrderByBidNumberDesc(UUID lotId, int round, BidStatus status);
}
