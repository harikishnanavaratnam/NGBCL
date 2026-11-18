package lk.ngbcl.registration.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lk.ngbcl.files.service.FileService;
import lk.ngbcl.registration.domain.RegistrationKind;
import lk.ngbcl.registration.service.PublicRegistrationService;
import lk.ngbcl.registration.service.PublicRegistrationService.PlayerSubmission;
import lk.ngbcl.registration.service.PublicRegistrationService.PublicPlayerForm;
import lk.ngbcl.registration.service.PublicRegistrationService.PublicTeamForm;
import lk.ngbcl.registration.service.PublicRegistrationService.PublicUploadResult;
import lk.ngbcl.registration.service.PublicRegistrationService.SessionTicket;
import lk.ngbcl.registration.service.PublicRegistrationService.SubmissionReceipt;
import lk.ngbcl.registration.service.PublicRegistrationService.TeamSubmission;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous registration endpoints. The {@code X-Registration-Token} header carries the
 * short-lived session token; it is never put in URLs (so it doesn't end up in logs).
 */
@RestController
@RequestMapping("/api/public/registration")
public class PublicRegistrationController {

    static final String TOKEN = "X-Registration-Token";

    private final PublicRegistrationService service;

    public PublicRegistrationController(PublicRegistrationService service) {
        this.service = service;
    }

    public record StartRequest(@NotNull RegistrationKind kind) {
    }

    public record UploadRequest(@Size(max = 255) String filename, @NotBlank @Size(max = 100) String contentType,
            @Positive long size) {
    }

    public record NameCheck(boolean available) {
    }

    @GetMapping("/player/form")
    public ResponseEntity<PublicPlayerForm> playerForm() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.playerForm());
    }

    @GetMapping("/team/form")
    public ResponseEntity<PublicTeamForm> teamForm() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.teamForm());
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionTicket start(@Valid @RequestBody StartRequest body) {
        return service.startSession(body.kind());
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public FileService.UploadTicket upload(@RequestHeader(value = TOKEN, required = false) String token, @Valid @RequestBody UploadRequest body) {
        return service.createUpload(token, body.filename(), body.contentType(), body.size());
    }

    @PostMapping("/uploads/{id}/complete")
    public PublicUploadResult complete(@RequestHeader(value = TOKEN, required = false) String token, @PathVariable UUID id) {
        return service.completeUpload(token, id);
    }

    @PostMapping("/player")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionReceipt submitPlayer(@RequestHeader(value = TOKEN, required = false) String token, @RequestBody PlayerSubmission body) {
        return service.submitPlayer(token, body);
    }

    @PostMapping("/team")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionReceipt submitTeam(@RequestHeader(value = TOKEN, required = false) String token, @RequestBody TeamSubmission body) {
        return service.submitTeam(token, body);
    }

    @GetMapping("/team/name-available")
    public ResponseEntity<NameCheck> nameAvailable(@RequestParam @Size(max = 60) String name) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new NameCheck(service.nameAvailable(name)));
    }
}
