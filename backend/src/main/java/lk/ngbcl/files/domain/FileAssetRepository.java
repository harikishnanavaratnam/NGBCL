package lk.ngbcl.files.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileAssetRepository extends JpaRepository<FileAsset, UUID> {

    List<FileAsset> findByUploadStatusAndCreatedAtBefore(UploadStatus status, Instant before, Limit limit);

    /**
     * Public-registration uploads that never became part of a submitted registration
     * (the applicant abandoned the form or replaced the image). Personal photos are not kept.
     */
    @Query(value = """
            SELECT f.* FROM file_assets f
            WHERE f.upload_session_id IS NOT NULL AND f.upload_status = 'UPLOADED' AND f.created_at < :before
              AND NOT EXISTS (SELECT 1 FROM player_registrations r WHERE r.photo_asset_id = f.id)
              AND NOT EXISTS (SELECT 1 FROM team_registrations r WHERE r.logo_asset_id = f.id)
            LIMIT 200
            """, nativeQuery = true)
    List<FileAsset> findOrphanedSessionUploads(@Param("before") Instant before);
}
