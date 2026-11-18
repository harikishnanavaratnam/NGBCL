package lk.ngbcl.team.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.team.domain.TeamStatus;
import lk.ngbcl.team.service.TeamService;
import lk.ngbcl.team.service.TeamService.PublicTeam;
import lk.ngbcl.team.service.TeamService.TeamInput;
import lk.ngbcl.team.service.TeamService.TeamView;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamController {

    private final TeamService teams;

    public TeamController(TeamService teams) {
        this.teams = teams;
    }

    public record TeamRequest(@NotBlank @Size(max = 40) String name, @Size(max = 3) String shortCode, Short colorSlot,
            @Size(max = 300) String description, UUID logoAssetId, @Size(max = 120) String contactName,
            @Email @Size(max = 254) String contactEmail, @Size(max = 30) String contactPhone, Long version) {

        TeamInput input() {
            return new TeamInput(name, shortCode, colorSlot, description, logoAssetId, contactName, contactEmail,
                    contactPhone);
        }
    }

    public record FeeRequest(boolean received, Long version) {
    }

    public record StatusRequest(@NotNull TeamStatus status, @Size(max = 300) String reason, Long version) {
    }

    @GetMapping("/api/admin/teams")
    public List<TeamView> list() {
        return teams.list();
    }

    @PostMapping("/api/admin/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamView create(@Valid @RequestBody TeamRequest body) {
        return teams.create(body.input());
    }

    @PatchMapping("/api/admin/teams/{id}")
    public TeamView update(@PathVariable UUID id, @Valid @RequestBody TeamRequest body) {
        return teams.update(id, body.input(), body.version());
    }

    @PostMapping("/api/admin/teams/{id}/fee")
    public TeamView fee(@PathVariable UUID id, @RequestBody FeeRequest body) {
        return teams.setFeeReceived(id, body.received(), body.version());
    }

    @PostMapping("/api/admin/teams/{id}/status")
    public TeamView status(@PathVariable UUID id, @Valid @RequestBody StatusRequest body) {
        return teams.setStatus(id, body.status(), body.reason(), body.version());
    }

    @GetMapping("/api/public/teams")
    public ResponseEntity<List<PublicTeam>> publicList() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(teams.publicList());
    }
}
