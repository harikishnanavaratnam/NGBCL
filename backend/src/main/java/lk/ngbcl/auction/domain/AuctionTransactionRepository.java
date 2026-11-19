package lk.ngbcl.auction.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionTransactionRepository extends JpaRepository<AuctionTransaction, UUID> {

    Optional<AuctionTransaction> findByLotIdAndStatus(UUID lotId, String status);

    List<AuctionTransaction> findByAuctionIdAndStatus(UUID auctionId, String status);
}
