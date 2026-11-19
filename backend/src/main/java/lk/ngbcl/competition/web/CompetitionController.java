package lk.ngbcl.competition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.competition.domain.CompetitionFormat;
import lk.ngbcl.competition.domain.GenderRule;
import lk.ngbcl.competition.service.CompetitionDtos.FixtureView;
import lk.ngbcl.competition.service.CompetitionDtos.FixturesView;
import lk.ngbcl.competition.service.CompetitionDtos.GroupsView;
import lk.ngbcl.competition.service.CompetitionDtos.PublishPreview;
import lk.ngbcl.competition.service.CompetitionDtos.RosterView;
import lk.ngbcl.competition.service.CompetitionDtos.SetupView;
import lk.ngbcl.competition.service.CompetitionReadModel;
import lk.ngbcl.competition.service.CompetitionSetupService;
import lk.ngbcl.competition.service.CompetitionSetupService.CategoryInput;
import lk.ngbcl.competition.service.CompetitionSetupService.CourtInput;
import lk.ngbcl.competition.service.CompetitionSetupService.SettingsInput;
import lk.ngbcl.competition.service.FixtureService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin › Competition: format, categories, template, courts, groups, fixtures (FIXTURE_MANAGE). */
@RestController
@RequestMapping("/api/admin/competition")
public class CompetitionController {

    private final CompetitionSetupService setup;
    private final FixtureService fixtures;
    private final CompetitionReadModel read;

    public CompetitionController(CompetitionSetupService setup, FixtureService fixtures, CompetitionReadModel read) {
        this.setup = setup;
        this.fixtures = fixtures;
        this.read = read;
    }

    public record SettingsRequest(@NotNull CompetitionFormat format, int groupCount, int qualifiersPerGroup,
            boolean thirdPlaceMatch, int maxMatchesPerPlayer, int matchMinutes, int restMinutes, Long version) {
    }

    public record CategoryRequest(String code, String name, int playersPerSide, GenderRule genderRule, Boolean active,
            Long version) {
    }

    public record TemplateRequest(@NotNull List<UUID> categoryIds) {
    }

    public record CourtRequest(String name, Boolean active, Long version) {
    }

    public record GroupsRequest(@NotNull List<List<UUID>> groups) {
    }

    public record DrawRequest(@NotNull List<UUID> teamIds) {
    }

    public record GenerateRequest(List<UUID> seedOrder) {
    }

    public record ScheduleRequest(Instant scheduledAt, UUID courtId, Long version) {
    }

    public record TeamsRequest(UUID teamAId, UUID teamBId, Long version) {
    }

    public record LineupRequest(List<UUID> sideA, List<UUID> sideB, UUID scorerUserId, Long version) {
    }

    public record PublishRequest(List<UUID> fixtureIds) {
    }

    public record PublishResult(int published, FixturesView view) {
    }

    // ---- Setup --------------------------------------------------------------------------------

    @GetMapping("/setup")
    public SetupView setup() {
        return setup.setup();
    }

    @PutMapping("/settings")
    public SetupView settings(@Valid @RequestBody SettingsRequest r) {
        return setup.updateSettings(new SettingsInput(r.format(), r.groupCount(), r.qualifiersPerGroup(),
                r.thirdPlaceMatch(), r.maxMatchesPerPlayer(), r.matchMinutes(), r.restMinutes(), r.version()));
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public SetupView createCategory(@RequestBody CategoryRequest r) {
        return setup.saveCategory(null, input(r));
    }

    @PutMapping("/categories/{id}")
    public SetupView updateCategory(@PathVariable UUID id, @RequestBody CategoryRequest r) {
        return setup.saveCategory(id, input(r));
    }

    private static CategoryInput input(CategoryRequest r) {
        return new CategoryInput(r.code(), r.name(), r.playersPerSide(), r.genderRule(), r.active(), r.version());
    }

    @PutMapping("/template")
    public SetupView template(@Valid @RequestBody TemplateRequest r) {
        return setup.saveTemplate(r.categoryIds());
    }

    @PostMapping("/courts")
    @ResponseStatus(HttpStatus.CREATED)
    public SetupView createCourt(@RequestBody CourtRequest r) {
        return setup.saveCourt(null, new CourtInput(r.name(), r.active(), r.version()));
    }

    @PutMapping("/courts/{id}")
    public SetupView updateCourt(@PathVariable UUID id, @RequestBody CourtRequest r) {
        return setup.saveCourt(id, new CourtInput(r.name(), r.active(), r.version()));
    }

    // ---- Groups --------------------------------------------------------------------------------

    @GetMapping("/groups")
    public GroupsView groups() {
        return read.groups();
    }

    @PutMapping("/groups")
    public GroupsView saveGroups(@Valid @RequestBody GroupsRequest r) {
        fixtures.saveGroups(r.groups());
        return read.groups();
    }

    /** Snake draw from a seed order (the client shuffles for a random draw). */
    @PostMapping("/groups/draw")
    public GroupsView draw(@Valid @RequestBody DrawRequest r) {
        fixtures.snakeDraw(r.teamIds());
        return read.groups();
    }

    // ---- Fixtures ------------------------------------------------------------------------------

    @GetMapping("/fixtures")
    public FixturesView list() {
        return read.fixtures();
    }

    @PostMapping("/fixtures/generate")
    public FixturesView generate(@RequestBody(required = false) GenerateRequest r) {
        fixtures.generate(r == null ? null : r.seedOrder());
        return read.fixtures();
    }

    @DeleteMapping("/fixtures")
    public FixturesView deleteAll() {
        fixtures.deleteAll();
        return read.fixtures();
    }

    @GetMapping("/fixtures/{id}")
    public FixtureView one(@PathVariable UUID id) {
        return read.fixture(id);
    }

    @GetMapping("/fixtures/{id}/rosters")
    public List<RosterView> rosters(@PathVariable UUID id) {
        return read.rosters(id);
    }

    @PutMapping("/fixtures/{id}/schedule")
    public FixtureView schedule(@PathVariable UUID id, @RequestBody ScheduleRequest r) {
        fixtures.schedule(id, r.scheduledAt(), r.courtId(), r.version());
        return read.fixture(id);
    }

    @PutMapping("/fixtures/{id}/teams")
    public FixtureView teams(@PathVariable UUID id, @RequestBody TeamsRequest r) {
        fixtures.setTeams(id, r.teamAId(), r.teamBId(), r.version());
        return read.fixture(id);
    }

    @PutMapping("/fixtures/{id}/matches/{matchId}/lineup")
    public FixtureView lineup(@PathVariable UUID id, @PathVariable UUID matchId, @RequestBody LineupRequest r) {
        fixtures.setLineup(id, matchId, r.sideA(), r.sideB(), r.scorerUserId(), r.version());
        return read.fixture(id);
    }

    @GetMapping("/fixtures/publish-preview")
    public PublishPreview preview() {
        return read.publishPreview();
    }

    @PostMapping("/fixtures/publish")
    public PublishResult publish(@RequestBody(required = false) PublishRequest r) {
        int n = fixtures.publish(r == null ? null : r.fixtureIds());
        return new PublishResult(n, read.fixtures());
    }
}
