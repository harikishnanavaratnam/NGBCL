package lk.ngbcl.files.service;

import java.util.UUID;
import lk.ngbcl.files.domain.FilePurpose;

/**
 * Supplied by the tournament module so the files module doesn't depend on it:
 * which edition uploads belong to, and the configured limits (e.g. player photo size).
 */
public interface UploadContext {

    UUID currentTournamentId();

    FileService.UploadLimits limitsFor(FilePurpose purpose);
}
