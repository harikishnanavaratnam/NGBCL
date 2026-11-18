package lk.ngbcl.player.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.player.domain.TournamentPlayerStatus;
import lk.ngbcl.player.service.PlayerService;
import lk.ngbcl.player.service.PlayerService.PlayerRow;
import lk.ngbcl.player.service.PlayerService.PublicPlayer;
import lk.ngbcl.shared.web.PageResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlayerController {

    private final PlayerService players;

    public PlayerController(PlayerService players) {
        this.players = players;
    }

    public record StatusRequest(@NotNull TournamentPlayerStatus status, @Size(max = 300) String reason, Long version) {
    }

    public record CategoryRequest(UUID categoryId, Long version) {
    }

    @GetMapping("/api/admin/players")
    public PageResponse<PlayerRow> directory(@RequestParam(required = false) String q,
            @RequestParam(required = false) TournamentPlayerStatus status,
            @RequestParam(required = false) UUID categoryId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return players.directory(new PlayerService.DirectoryFilter(q, status, categoryId, page, size));
    }

    @PostMapping("/api/admin/players/{id}/status")
    public PlayerRow status(@PathVariable UUID id, @Valid @RequestBody StatusRequest body) {
        return players.changeStatus(id, body.status(), body.reason(), body.version());
    }

    @PostMapping("/api/admin/players/{id}/category")
    public PlayerRow category(@PathVariable UUID id, @RequestBody CategoryRequest body) {
        return players.changeCategory(id, body.categoryId(), body.version());
    }

    @GetMapping("/api/public/players/{slug}")
    public ResponseEntity<PlayerService.PublicPlayerDetail> publicDetail(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(players.publicDetail(slug));
    }

    @GetMapping("/api/public/players")
    public ResponseEntity<List<PublicPlayer>> publicList() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(players.publicList());
    }
}
