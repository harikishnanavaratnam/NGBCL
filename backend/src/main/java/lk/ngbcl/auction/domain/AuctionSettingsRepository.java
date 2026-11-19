package lk.ngbcl.auction.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionSettingsRepository extends JpaRepository<AuctionSettings, UUID> {
}
