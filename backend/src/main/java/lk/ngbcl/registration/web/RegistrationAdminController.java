package lk.ngbcl.registration.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.registration.domain.PlayerRegistration.PlayerDetails;
import lk.ngbcl.registration.domain.RegistrationStatus;
import lk.ngbcl.registration.service.PlayerReviewService;
import lk.ngbcl.registration.service.PlayerReviewService.DuplicateAction;
import lk.ngbcl.registration.service.RegistrationDtos.BulkResult;
import lk.ngbcl.registration.service.RegistrationDtos.PlayerRegistrationDetail;
import lk.ngbcl.registration.service.RegistrationDtos.PlayerRegistrationRow;
import lk.ngbcl.registration.service.RegistrationDtos.Summary;
import lk.ngbcl.registration.service.RegistrationDtos.TeamRegistrationDetail;
import lk.ngbcl.registration.service.RegistrationDtos.TeamRegistrationRow;
import lk.ngbcl.registration.service.TeamReviewService;
import lk.ngbcl.shared.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/registrations")
public class RegistrationAdminController {

    private final PlayerReviewService players;
    private final TeamReviewService teams;

    public RegistrationAdminController(PlayerReviewService players, TeamReviewService teams) {
        this.players = players;
        this.teams = teams;
    }

    public record VerifyRequest(boolean approve, @Size(max = 255) String reason) {
    }

    public record VersionRequest(Long version) {
    }

    public record RejectRequest(@NotBlank @Size(max = 120) String reason, @Size(max = 500) String message,
            Long version) {
    }

    public record NoteRequest(@NotBlank @Size(max = 1000) String body) {
    }

    public record ResolveRequest(@NotNull DuplicateAction action) {
    }

    public record BulkRequest(@NotEmpty @Size(max = 100) List<UUID> ids) {
    }

    public record EditPlayerRequest(@NotBlank @Size(max = 120) String fullName, LocalDate dateOfBirth,
            @Size(max = 20) String gender, @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 30) String phone,
            @Size(max = 300) String address, @Size(max = 80) String homeArea, @Size(max = 300) String experience,
            @Size(max = 120) String emergencyName, @Size(max = 30) String emergencyPhone, UUID skillCategoryId,
            Long version) {
    }

    public record ApproveTeamRequest(@Size(max = 3) String shortCode, Short colorSlot, Long version) {
    }

    @GetMapping("/summary")
    public Summary summary() {
        return players.summary();
    }

    // ---- Players ----------------------------------------------------------------------

    @GetMapping("/players")
    public PageResponse<PlayerRegistrationRow> listPlayers(@RequestParam(required = false) RegistrationStatus status,
            @RequestParam(required = false) String q, @RequestParam(required = false) Boolean duplicate,
            @RequestParam(required = false) UUID categoryId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return players.list(new PlayerReviewService.Filter(status, q, duplicate, categoryId, page, size));
    }

    @GetMapping("/players/{id}")
    public PlayerRegistrationDetail player(@PathVariable UUID id) {
        return players.get(id);
    }

    @PatchMapping("/players/{id}")
    public PlayerRegistrationDetail editPlayer(@PathVariable UUID id, @Valid @RequestBody EditPlayerRequest b) {
        return players.edit(id, new PlayerDetails(b.fullName(), b.dateOfBirth(), b.gender(), b.email(), b.phone(),
                b.address(), b.homeArea(), b.experience(), b.emergencyName(), b.emergencyPhone(), b.skillCategoryId()),
                b.version());
    }

    @PostMapping("/players/{id}/photo/verify")
    public PlayerRegistrationDetail verifyPhoto(@PathVariable UUID id, @Valid @RequestBody VerifyRequest b) {
        return players.verifyPhoto(id, b.approve(), b.reason());
    }

    @PostMapping("/players/{id}/duplicates/{duplicateId}/resolve")
    public PlayerRegistrationDetail resolve(@PathVariable UUID id, @PathVariable UUID duplicateId,
            @Valid @RequestBody ResolveRequest b) {
        return players.resolveDuplicate(id, duplicateId, b.action());
    }

    @PostMapping("/players/{id}/approve")
    public PlayerRegistrationDetail approvePlayer(@PathVariable UUID id, @RequestBody(required = false) VersionRequest b) {
        return players.approve(id, b == null ? null : b.version());
    }

    @PostMapping("/players/{id}/reject")
    public PlayerRegistrationDetail rejectPlayer(@PathVariable UUID id, @Valid @RequestBody RejectRequest b) {
        return players.reject(id, b.reason(), b.message(), b.version());
    }

    @PostMapping("/players/{id}/notes")
    public PlayerRegistrationDetail notePlayer(@PathVariable UUID id, @Valid @RequestBody NoteRequest b) {
        return players.addNote(id, b.body());
    }

    @PostMapping("/players/bulk-approve")
    public BulkResult bulkApprove(@Valid @RequestBody BulkRequest b) {
        return players.bulkApprove(b.ids());
    }

    // ---- Teams --------------------------------------------------------------------------

    @GetMapping("/teams")
    public PageResponse<TeamRegistrationRow> listTeams(@RequestParam(required = false) RegistrationStatus status,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return teams.list(new TeamReviewService.Filter(status, q, page, size));
    }

    @GetMapping("/teams/{id}")
    public TeamRegistrationDetail team(@PathVariable UUID id) {
        return teams.get(id);
    }

    @PostMapping("/teams/{id}/logo/verify")
    public TeamRegistrationDetail verifyLogo(@PathVariable UUID id, @Valid @RequestBody VerifyRequest b) {
        return teams.verifyLogo(id, b.approve(), b.reason());
    }

    @PostMapping("/teams/{id}/approve")
    public TeamRegistrationDetail approveTeam(@PathVariable UUID id,
            @RequestBody(required = false) ApproveTeamRequest b) {
        return teams.approve(id, b == null ? null : b.shortCode(), b == null ? null : b.colorSlot(),
                b == null ? null : b.version());
    }

    @PostMapping("/teams/{id}/reject")
    public TeamRegistrationDetail rejectTeam(@PathVariable UUID id, @Valid @RequestBody RejectRequest b) {
        return teams.reject(id, b.reason(), b.message(), b.version());
    }

    @PostMapping("/teams/{id}/notes")
    public TeamRegistrationDetail noteTeam(@PathVariable UUID id, @Valid @RequestBody NoteRequest b) {
        return teams.addNote(id, b.body());
    }
}
