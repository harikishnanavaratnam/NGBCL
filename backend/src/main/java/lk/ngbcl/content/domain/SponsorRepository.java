package lk.ngbcl.content.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SponsorRepository extends JpaRepository<Sponsor, UUID> {

    List<Sponsor> findByTournamentIdOrderByDisplayOrderAscNameAsc(UUID tournamentId);

    @Query("select count(s) from Sponsor s where s.tournamentId = :t and s.active = true and s.tier = lk.ngbcl.content.domain.SponsorTier.TITLE and s.id <> :exclude")
    long countOtherActiveTitle(@Param("t") UUID tournamentId, @Param("exclude") UUID exclude);

    @Query("select coalesce(max(s.displayOrder), 0) from Sponsor s where s.tournamentId = :t")
    int maxOrder(@Param("t") UUID tournamentId);
}
