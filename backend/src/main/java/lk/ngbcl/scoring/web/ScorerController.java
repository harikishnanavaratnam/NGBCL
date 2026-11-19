package lk.ngbcl.scoring.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.scoring.service.ScoringDtos.MatchState;
import lk.ngbcl.scoring.service.ScoringDtos.ScorerCard;
import lk.ngbcl.scoring.service.ScoringReadModel;
import lk.ngbcl.scoring.service.ScoringService;
import lk.ngbcl.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scorer app API (MATCH_SCORE: assigned matches; MATCH_SCORE_ANY: every match). Commands carry a
 * client event id so a retried tap is applied once; each answers with the fresh match state.
 */
@RestController
@RequestMapping("/api/scorer")
public class ScorerController {

    private final ScoringService scoring;
    private final ScoringReadModel read;

    public ScorerController(ScoringService scoring, ScoringReadModel read) {
        this.scoring = scoring;
        this.read = read;
    }

    public record StartRequest(@NotNull String firstServer, @NotBlank String clientEventId) {
    }

    public record PointRequest(@NotNull String side, @NotBlank String clientEventId) {
    }

    public record EventRequest(@NotBlank String clientEventId) {
    }

    public record ManualRequest(@NotNull List<List<Integer>> games, String firstServer, @NotBlank String clientEventId) {
    }

    public record ConcedeRequest(@NotNull String type, @NotNull String side, String note, @NotBlank String clientEventId) {
    }

    @GetMapping("/matches")
    @PreAuthorize("hasAnyAuthority('MATCH_SCORE', 'MATCH_SCORE_ANY')")
    public List<ScorerCard> mine() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean any = auth.getAuthorities().stream().anyMatch(a -> "MATCH_SCORE_ANY".equals(a.getAuthority()));
        return read.scorerMatches(CurrentUser.requireId(), any);
    }

    @GetMapping("/matches/{id}")
    public MatchState one(@PathVariable UUID id) {
        return scoring.view(id);
    }

    @PostMapping("/matches/{id}/start")
    public MatchState start(@PathVariable UUID id, @Valid @RequestBody StartRequest r) {
        scoring.start(id, r.firstServer(), r.clientEventId());
        return scoring.view(id);
    }

    @PostMapping("/matches/{id}/point")
    public MatchState point(@PathVariable UUID id, @Valid @RequestBody PointRequest r) {
        scoring.point(id, r.side(), r.clientEventId());
        return scoring.view(id);
    }

    @PostMapping("/matches/{id}/undo")
    public MatchState undo(@PathVariable UUID id, @Valid @RequestBody EventRequest r) {
        scoring.undo(id, r.clientEventId());
        return scoring.view(id);
    }

    @PostMapping("/matches/{id}/manual")
    public MatchState manual(@PathVariable UUID id, @Valid @RequestBody ManualRequest r) {
        scoring.manual(id, games(r.games()), r.firstServer(), r.clientEventId());
        return scoring.view(id);
    }

    @PostMapping("/matches/{id}/concede")
    public MatchState concede(@PathVariable UUID id, @Valid @RequestBody ConcedeRequest r) {
        scoring.concede(id, r.type(), r.side(), r.note(), r.clientEventId());
        return scoring.view(id);
    }

    @PostMapping("/matches/{id}/submit")
    public MatchState submit(@PathVariable UUID id, @Valid @RequestBody EventRequest r) {
        scoring.submit(id, r.clientEventId());
        return scoring.view(id);
    }

    static List<int[]> games(List<List<Integer>> in) {
        return in.stream().map(g -> {
            if (g == null || g.size() != 2 || g.get(0) == null || g.get(1) == null) {
                throw lk.ngbcl.shared.web.ApiException.badRequest("INVALID_RESULT", "Each game needs two scores.");
            }
            return new int[] {g.get(0), g.get(1)};
        }).toList();
    }
}
