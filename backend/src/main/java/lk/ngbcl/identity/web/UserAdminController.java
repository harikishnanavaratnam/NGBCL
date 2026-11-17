package lk.ngbcl.identity.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lk.ngbcl.identity.domain.UserStatus;
import lk.ngbcl.identity.service.UserAdminService;
import lk.ngbcl.identity.service.UserAdminService.RoleSummary;
import lk.ngbcl.identity.service.UserAdminService.UserSummary;
import lk.ngbcl.shared.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    public record CreateUserRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 120) String displayName, @NotBlank @Size(min = 12, max = 200) String password,
            @NotEmpty Set<String> roles) {
    }

    public record UpdateUserRequest(@Size(max = 120) String displayName, Set<String> roles, UserStatus status) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 12, max = 200) String password) {
    }

    @GetMapping("/users")
    public PageResponse<UserSummary> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(page, size);
    }

    @GetMapping("/roles")
    public List<RoleSummary> roles() {
        return service.roles();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary create(@Valid @RequestBody CreateUserRequest body) {
        return service.create(body.email(), body.displayName(), body.password(), body.roles());
    }

    @PatchMapping("/users/{id}")
    public UserSummary update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest body) {
        return service.update(id, body.displayName(), body.roles(), body.status());
    }

    @PostMapping("/users/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest body) {
        service.resetPassword(id, body.password());
    }
}
