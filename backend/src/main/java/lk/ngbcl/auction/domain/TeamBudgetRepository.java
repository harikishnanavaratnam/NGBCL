package lk.ngbcl.auction.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamBudgetRepository extends JpaRepository<TeamBudget, UUID> {

    List<TeamBudget> findByAuctionId(UUID auctionId);

    Optional<TeamBudget> findByAuctionIdAndTeamId(UUID auctionId, UUID teamId);
}
