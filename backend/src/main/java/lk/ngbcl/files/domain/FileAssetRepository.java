package lk.ngbcl.files.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileAssetRepository extends JpaRepository<FileAsset, UUID> {

    List<FileAsset> findByUploadStatusAndCreatedAtBefore(UploadStatus status, Instant before, Limit limit);
}
