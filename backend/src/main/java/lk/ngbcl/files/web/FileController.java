package lk.ngbcl.files.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lk.ngbcl.files.domain.FilePurpose;
import lk.ngbcl.files.service.FileAssetView;
import lk.ngbcl.files.service.FileService;
import lk.ngbcl.files.service.UploadContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff uploads (branding, admin-created teams, photo replacements). Public registration
 * uploads get their own token-scoped endpoints in M2.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService files;
    private final UploadContext context;

    public FileController(FileService files, UploadContext context) {
        this.files = files;
        this.context = context;
    }

    public record CreateUploadRequest(@NotNull FilePurpose purpose, @Size(max = 255) String filename,
            @NotBlank @Size(max = 100) String contentType, @Positive long size) {
    }

    public record VerifyRequest(boolean approve, @Size(max = 255) String reason) {
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('FILE_UPLOAD')")
    public FileService.UploadTicket createUpload(@Valid @RequestBody CreateUploadRequest body) {
        return files.createUpload(context.currentTournamentId(), body.purpose(), body.filename(), body.contentType(),
                body.size(), context.limitsFor(body.purpose()));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('FILE_UPLOAD')")
    public FileAssetView complete(@PathVariable UUID id) {
        FileAssetView current = files.get(id);
        return files.complete(id, context.limitsFor(current.purpose()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FILE_UPLOAD')")
    public FileAssetView get(@PathVariable UUID id) {
        return files.get(id);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyAuthority('REGISTRATION_REVIEW', 'SETTINGS_MANAGE', 'DIRECTORY_MANAGE')")
    public FileAssetView verify(@PathVariable UUID id, @Valid @RequestBody VerifyRequest body) {
        return files.verify(id, body.approve(), body.reason());
    }
}
