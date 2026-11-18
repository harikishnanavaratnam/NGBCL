package lk.ngbcl.content.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    List<Announcement> findByTournamentIdOrderByUpdatedAtDesc(UUID tournamentId);

    boolean existsByTournamentIdAndSlug(UUID tournamentId, String slug);

    Optional<Announcement> findByTournamentIdAndSlug(UUID tournamentId, String slug);

    /** Public list: live items, pinned first, newest first. */
    @Query("""
            select a from Announcement a
            where a.tournamentId = :t and a.status = lk.ngbcl.content.domain.AnnouncementStatus.PUBLISHED
              and a.publishAt <= :now
            order by a.pinned desc, a.publishAt desc, a.id desc
            """)
    Page<Announcement> findLive(@Param("t") UUID tournamentId, @Param("now") Instant now, Pageable page);
}
