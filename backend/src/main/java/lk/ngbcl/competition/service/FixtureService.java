package lk.ngbcl.competition.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.competition.domain.CompetitionFormat;
import lk.ngbcl.competition.domain.CompetitionGroup;
import lk.ngbcl.competition.domain.CompetitionGroupRepository;
import lk.ngbcl.competition.domain.CompetitionSettings;
import lk.ngbcl.competition.domain.Court;
import lk.ngbcl.competition.domain.CourtRepository;
import lk.ngbcl.competition.domain.FixtureStatus;
import lk.ngbcl.competition.domain.FixtureTemplateItem;
import lk.ngbcl.competition.domain.FixtureTemplateItemRepository;
import lk.ngbcl.competition.domain.GenderRule;
import lk.ngbcl.competition.domain.GroupTeam;
import lk.ngbcl.competition.domain.GroupTeamRepository;
import lk.ngbcl.competition.domain.Match;
import lk.ngbcl.competition.domain.MatchCategory;
import lk.ngbcl.competition.domain.MatchCategoryRepository;
import lk.ngbcl.competition.domain.MatchPlayer;
import lk.ngbcl.competition.domain.MatchPlayerRepository;
import lk.ngbcl.competition.domain.MatchRepository;
import lk.ngbcl.competition.domain.MatchStatus;
import lk.ngbcl.competition.domain.Stage;
import lk.ngbcl.competition.domain.StageRepository;
import lk.ngbcl.competition.domain.StageType;
import lk.ngbcl.competition.domain.TeamFixture;
import lk.ngbcl.competition.domain.TeamFixtureRepository;
import lk.ngbcl.competition.service.FixtureGenerator.Pairing;
import lk.ngbcl.competition.service.RosterSource.RosterPlayer;
import lk.ngbcl.identity.service.StaffDirectory;
import lk.ngbcl.security.Permission;
import lk.ngbcl.shared.site.SiteRevalidator;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.FieldErrors;
import lk.ngbcl.team.service.TeamService;
import lk.ngbcl.team.service.TeamService.TeamCard;
import lk.ngbcl.tournament.service.TournamentService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groups, fixture generation, scheduling, line-ups and publishing (SRS §4.9). The API enforces
 * FIX-004 (two distinct teams), FIX-008 (players from the team's roster) and FIX-009 (no player,
 * team or court in two places at once, per the configured match length).
 */
@Service
public class FixtureService {

    static final String[] GROUP_NAMES = {"A", "B", "C", "D", "E", "F", "G", "H"};

    private final CompetitionSetupService setup;
    private final StageRepository stages;
    private final CompetitionGroupRepository groups;
    private final GroupTeamRepository groupTeams;
    private final TeamFixtureRepository fixtures;
    private final MatchRepository matches;
    private final MatchPlayerRepository lineups;
    private final MatchCategoryRepository categories;
    private final FixtureTemplateItemRepository template;
    private final CourtRepository courts;
    private final TeamService teams;
    private final ObjectProvider<RosterSource> rosters;
    private final StaffDirectory staff;
    private final TournamentService tournament;
    private final AuditService audit;
    private final SiteRevalidator site;
    private final EntityManager em;
    private final Clock clock;
    private final CompetitionReadModel read;

    public FixtureService(CompetitionSetupService setup, StageRepository stages, CompetitionGroupRepository groups,
            GroupTeamRepository groupTeams, TeamFixtureRepository fixtures, MatchRepository matches,
            MatchPlayerRepository lineups, MatchCategoryRepository categories, FixtureTemplateItemRepository template,
            CourtRepository courts, TeamService teams, ObjectProvider<RosterSource> rosters, StaffDirectory staff,
            TournamentService tournament, AuditService audit, SiteRevalidator site, EntityManager em, Clock clock,
            CompetitionReadModel read) {
        this.setup = setup;
        this.stages = stages;
        this.groups = groups;
        this.groupTeams = groupTeams;
        this.fixtures = fixtures;
        this.matches = matches;
        this.lineups = lineups;
        this.categories = categories;
        this.template = template;
        this.courts = courts;
        this.teams = teams;
        this.rosters = rosters;
        this.staff = staff;
        this.tournament = tournament;
        this.audit = audit;
        this.site = site;
        this.em = em;
        this.clock = clock;
        this.read = read;
    }

    public static final Set<Permission> SCORER_PERMISSIONS = Set.of(Permission.MATCH_SCORE, Permission.MATCH_SCORE_ANY);

    // ---- Groups and seeding ----------------------------------------------------------------

    /**
     * Replaces the group draw. Each inner list is one group, in seed order. Only possible before
     * fixtures exist (regenerating starts from the draw).
     */
    @Transactional
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public void saveGroups(List<List<UUID>> draw) {
        UUID t = tournament.currentTournamentId();
        CompetitionSettings s = setup.ensure(t);
        requireNoFixtures(t);
        if (draw == null || draw.size() != s.getGroupCount()) {
            throw ApiException.unprocessable("INVALID_DRAW", "Send exactly " + s.getGroupCount() + " groups.");
        }
        Set<UUID> active = teams.activeTeams(t).stream().map(TeamCard::id).collect(Collectors.toSet());
        Set<UUID> seen = new HashSet<>();
        for (List<UUID> g : draw) {
            for (UUID team : g) {
                if (!active.contains(team)) {
                    throw ApiException.unprocessable("INVALID_DRAW", "Only active teams can be drawn into groups.");
                }
                if (!seen.add(team)) {
                    throw ApiException.unprocessable("INVALID_DRAW", "A team can only be in one group.");
                }
            }
        }
        clearGroups(t);
        Instant now = clock.instant();
        for (int i = 0; i < draw.size(); i++) {
            CompetitionGroup g = groups.save(new CompetitionGroup(t, "Group " + GROUP_NAMES[i], i + 1, now));
            int seed = 0;
            for (UUID team : draw.get(i)) {
                groupTeams.save(new GroupTeam(g.getId(), team, ++seed));
            }
        }
        groups.flush();
        audit.record(AuditEvent.of("competition.groups.drawn", "Tournament", t).inTournament(t)
                .withChange(null, Map.of("groups", draw.size(), "teams", seen.size())));
    }

    /** Snake draw: seeds 1..n go A, B, …, then back …, B, A — so top seeds are spread out. */
    @Transactional
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public void snakeDraw(List<UUID> seedOrder) {
        UUID t = tournament.currentTournamentId();
        CompetitionSettings s = setup.ensure(t);
        int k = s.getGroupCount();
        List<List<UUID>> draw = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            draw.add(new ArrayList<>());
        }
        List<UUID> order = new ArrayList<>(new LinkedHashSet<>(seedOrder == null ? List.of() : seedOrder));
        for (int i = 0; i < order.size(); i++) {
            int round = i / k;
            int pos = i % k;
            draw.get(round % 2 == 0 ? pos : k - 1 - pos).add(order.get(i));
        }
        saveGroups(draw);
    }

    private void clearGroups(UUID t) {
        List<CompetitionGroup> existing = groups.findByTournamentIdOrderByGroupOrderAsc(t);
        if (!existing.isEmpty()) {
            groupTeams.deleteAllInBatch(groupTeams.findByGroupIdIn(existing.stream().map(CompetitionGroup::getId).toList()));
            groups.deleteAllInBatch(existing);
            groups.flush();
        }
    }

    // ---- Generation ----------------------------------------------------------------------

    public record GenerateResult(int fixtures, int matches) {
    }

    /**
     * Creates all fixtures for the configured format, each with the template's matches. Replaces
     * earlier drafts; refused once anything is published or under way.
     */
    @Transactional
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public GenerateResult generate(List<UUID> knockoutSeedOrder) {
        UUID t = tournament.currentTournamentId();
        CompetitionSettings s = setup.ensure(t);
        List<TeamFixture> existing = fixtures.findByTournamentIdOrderByFixtureNumberAsc(t);
        if (existing.stream().anyMatch(f -> f.isPublished() || f.isLocked())) {
            throw ApiException.conflict("FIXTURES_PUBLISHED",
                    "Fixtures are already published. Change them one by one instead of regenerating.");
        }
        List<MatchCategory> slots = templateCategories(t);
        if (slots.isEmpty()) {
            throw ApiException.unprocessable("NO_TEMPLATE", "Set the matches in a tie (fixture template) first.");
        }
        List<UUID> active = teams.activeTeams(t).stream().map(TeamCard::id).toList();
        if (active.size() < 2) {
            throw ApiException.unprocessable("NOT_ENOUGH_TEAMS", "At least two active teams are needed.");
        }
        deleteDrafts(t, existing);

        Gen gen = new Gen(t, slots, clock.instant());
        switch (s.getFormat()) {
            case ROUND_ROBIN -> league(gen, active, false, s);
            case ROUND_ROBIN_FINAL -> league(gen, active, true, s);
            case GROUPS_KNOCKOUT -> groupsThenKnockout(gen, s, active);
            case KNOCKOUT -> knockoutOnly(gen, s, active, knockoutSeedOrder);
        }
        fixtures.flush();
        audit.record(AuditEvent.of("competition.fixtures.generated", "Tournament", t).inTournament(t)
                .withChange(null, Map.of("format", s.getFormat(), "fixtures", gen.fixtureCount, "matches", gen.matchCount)));
        return new GenerateResult(gen.fixtureCount, gen.matchCount);
    }

    /** Running state while generating. */
    private final class Gen {
        final UUID t;
        final List<MatchCategory> slots;
        final Instant now;
        int number;
        int stageOrder;
        int fixtureCount;
        int matchCount;

        Gen(UUID t, List<MatchCategory> slots, Instant now) {
            this.t = t;
            this.slots = slots;
            this.now = now;
        }

        Stage stage(StageType type, String name) {
            return stages.save(new Stage(t, type, name, ++stageOrder, now));
        }

        TeamFixture fixture(Stage st, UUID groupId, int round, String roundName, Integer bracketPos, UUID a, UUID b) {
            TeamFixture f = fixtures.save(new TeamFixture(t, st.getId(), groupId, ++number, round, roundName, bracketPos));
            f.setTeams(a, b);
            int order = 0;
            for (MatchCategory c : slots) {
                matches.save(new Match(f.getId(), ++order, c.getId(), c.getPlayersPerSide()));
                matchCount++;
            }
            fixtureCount++;
            return f;
        }
    }

    private void league(Gen gen, List<UUID> active, boolean withFinal, CompetitionSettings s) {
        Stage st = gen.stage(StageType.LEAGUE, "League");
        for (Pairing p : FixtureGenerator.roundRobin(active)) {
            gen.fixture(st, null, p.round(), "Round " + p.round(), null, p.a(), p.b());
        }
        if (withFinal) {
            Stage ko = gen.stage(StageType.KNOCKOUT, "Final");
            TeamFixture fin = gen.fixture(ko, null, 1, "Final", 1, null, null);
            fin.setSources("League #1", "League #2");
            if (s.isThirdPlaceMatch() && active.size() >= 4) {
                TeamFixture third = gen.fixture(ko, null, 1, "3rd place", 2, null, null);
                third.setSources("League #3", "League #4");
            }
        }
    }

    private void groupsThenKnockout(Gen gen, CompetitionSettings s, List<UUID> active) {
        List<CompetitionGroup> gs = groups.findByTournamentIdOrderByGroupOrderAsc(gen.t);
        if (gs.size() != s.getGroupCount()) {
            throw ApiException.unprocessable("NO_DRAW", "Draw the teams into " + s.getGroupCount() + " groups first.");
        }
        Map<UUID, List<GroupTeam>> members = groupTeams.findByGroupIdIn(gs.stream().map(CompetitionGroup::getId).toList())
                .stream().collect(Collectors.groupingBy(GroupTeam::getGroupId));
        Set<UUID> drawn = members.values().stream().flatMap(List::stream).map(GroupTeam::getTeamId).collect(Collectors.toSet());
        Set<UUID> activeSet = new HashSet<>(active);
        if (!drawn.equals(activeSet)) {
            throw ApiException.unprocessable("NO_DRAW", "Every active team must be in exactly one group. Redo the draw.");
        }
        int qpg = s.getQualifiersPerGroup();
        for (CompetitionGroup g : gs) {
            int size = members.getOrDefault(g.getId(), List.of()).size();
            if (size < 2) {
                throw ApiException.unprocessable("NO_DRAW", g.getName() + " needs at least two teams.");
            }
            if (size < qpg) {
                throw ApiException.unprocessable("NO_DRAW", g.getName() + " has fewer teams than qualify from it.");
            }
        }
        Stage gst = gen.stage(StageType.GROUP, "Group stage");
        for (CompetitionGroup g : gs) {
            List<UUID> ids = members.get(g.getId()).stream()
                    .sorted((x, y) -> Integer.compare(x.getSeed(), y.getSeed())).map(GroupTeam::getTeamId).toList();
            for (Pairing p : FixtureGenerator.roundRobin(ids)) {
                gen.fixture(gst, g.getId(), p.round(), g.getName() + " · Round " + p.round(), null, p.a(), p.b());
            }
        }
        // Knockout slots: seeds are all group winners (A1, B1 …), then runners-up (A2, B2 …), …
        List<String> seedLabels = new ArrayList<>();
        for (int pos = 1; pos <= qpg; pos++) {
            for (CompetitionGroup g : gs) {
                seedLabels.add(g.getName() + " #" + pos);
            }
        }
        // Standard bracket order already keeps group-mates apart in round one (A1 v B2, B1 v A2 …).
        bracket(gen, s, seedLabels.size(), i -> null, i -> seedLabels.get(i - 1), null);
    }

    private void knockoutOnly(Gen gen, CompetitionSettings s, List<UUID> active, List<UUID> seedOrder) {
        List<UUID> seeds = new ArrayList<>();
        if (seedOrder != null) {
            for (UUID id : new LinkedHashSet<>(seedOrder)) {
                if (active.contains(id)) {
                    seeds.add(id);
                }
            }
        }
        for (UUID id : active) {
            if (!seeds.contains(id)) {
                seeds.add(id);
            }
        }
        bracket(gen, s, seeds.size(), i -> seeds.get(i - 1), i -> null, null);
    }

    /**
     * Builds a single-elimination bracket for {@code n} entrants. {@code team(i)} gives the team for
     * seed i (or null if not known yet), {@code label(i)} the slot label. Byes (when n isn't a power
     * of two) send top seeds straight into round two. Adds a 3rd-place match if configured.
     */
    private void bracket(Gen gen, CompetitionSettings s, int n, Function<Integer, UUID> team,
            Function<Integer, String> label, List<Integer> seedMap) {
        if (n < 2) {
            return;
        }
        Stage st = gen.stage(StageType.KNOCKOUT, "Knockout");
        int size = FixtureGenerator.nextPowerOfTwo(n);
        List<Integer> order = FixtureGenerator.bracketOrder(size);
        int rounds = Integer.numberOfTrailingZeros(size);

        // Create rounds from the final backwards so each fixture knows where its winner goes.
        List<List<TeamFixture>> byRound = new ArrayList<>();
        for (int r = 0; r < rounds; r++) {
            byRound.add(new ArrayList<>());
        }
        // Round r (0 = first) has size / 2^(r+1) fixtures; create first round last? Numbering should
        // follow playing order, so create first round first, then link forward.
        for (int r = 0; r < rounds; r++) {
            int teamsInRound = size >> r;
            int count = teamsInRound / 2;
            for (int i = 0; i < count; i++) {
                if (r == 0) {
                    int seedA = order.get(2 * i);
                    int seedB = order.get(2 * i + 1);
                    if (seedB > n || seedA > n) {
                        byRound.get(0).add(null); // bye: no fixture
                        continue;
                    }
                    int sa = seedMap == null ? seedA : seedMap.get(seedA - 1);
                    int sb = seedMap == null ? seedB : seedMap.get(seedB - 1);
                    TeamFixture f = gen.fixture(st, null, 1, FixtureGenerator.roundName(teamsInRound), i + 1,
                            team.apply(sa), team.apply(sb));
                    f.setSources(label.apply(sa), label.apply(sb));
                    byRound.get(0).add(f);
                } else {
                    TeamFixture f = gen.fixture(st, null, r + 1, FixtureGenerator.roundName(teamsInRound), i + 1, null, null);
                    byRound.get(r).add(f);
                }
            }
        }
        // Link winners forward and fill byes.
        for (int r = 0; r < rounds - 1; r++) {
            List<TeamFixture> cur = byRound.get(r);
            List<TeamFixture> next = byRound.get(r + 1);
            int teamsInRound = size >> r;
            for (int i = 0; i < cur.size(); i++) {
                TeamFixture target = next.get(i / 2);
                String side = i % 2 == 0 ? "A" : "B";
                TeamFixture f = cur.get(i);
                if (f == null) {
                    // bye: the seeded team goes straight through
                    int seedA = order.get(2 * i);
                    int seedB = order.get(2 * i + 1);
                    int seed = Math.min(seedA, seedB);
                    int mapped = seedMap == null ? seed : seedMap.get(seed - 1);
                    target.setTeam(side, team.apply(mapped));
                    setSource(target, side, label.apply(mapped) != null ? label.apply(mapped) + " (bye)" : null);
                } else {
                    f.winnerTo(target.getId(), side);
                    setSource(target, side, "Winner of " + FixtureGenerator.roundCode(teamsInRound) + (i + 1));
                }
            }
        }
        // Third place: losers of the semi-finals.
        if (s.isThirdPlaceMatch() && rounds >= 2) {
            List<TeamFixture> semis = byRound.get(rounds - 2);
            TeamFixture third = gen.fixture(st, null, rounds, "3rd place", 2, null, null);
            for (int i = 0; i < semis.size(); i++) {
                TeamFixture sf = semis.get(i);
                String side = i % 2 == 0 ? "A" : "B";
                if (sf != null) {
                    sf.loserTo(third.getId(), side);
                    setSource(third, side, "Loser of SF" + (i + 1));
                }
            }
        }
    }

    private static void setSource(TeamFixture f, String side, String label) {
        if ("A".equals(side)) {
            f.setSources(label, f.getSourceB());
        } else {
            f.setSources(f.getSourceA(), label);
        }
    }

    private void deleteDrafts(UUID t, List<TeamFixture> existing) {
        if (!existing.isEmpty()) {
            List<UUID> ids = existing.stream().map(TeamFixture::getId).toList();
            List<Match> ms = matches.findByFixtureIdInOrderByMatchOrderAsc(ids);
            if (!ms.isEmpty()) {
                lineups.deleteAllInBatch(lineups.findByMatchIdIn(ms.stream().map(Match::getId).toList()));
                matches.deleteAllInBatch(ms);
            }
            em.createQuery("update TeamFixture f set f.winnerToFixtureId = null, f.loserToFixtureId = null where f.tournamentId = :t")
                    .setParameter("t", t).executeUpdate();
            fixtures.deleteAllInBatch(existing);
        }
        List<Stage> old = stages.findByTournamentIdOrderByStageOrderAsc(t);
        if (!old.isEmpty()) {
            stages.deleteAllInBatch(old);
        }
        em.flush();
        em.clear();
    }

    private List<MatchCategory> templateCategories(UUID t) {
        Map<UUID, MatchCategory> byId = categories.findByTournamentIdOrderByDisplayOrderAscCodeAsc(t).stream()
                .collect(Collectors.toMap(MatchCategory::getId, Function.identity()));
        return template.findByTournamentIdOrderByPositionAsc(t).stream().map(FixtureTemplateItem::getCategoryId)
                .map(byId::get).filter(Objects::nonNull).toList();
    }

    private void requireNoFixtures(UUID t) {
        if (!fixtures.findByTournamentIdOrderByFixtureNumberAsc(t).isEmpty()) {
            throw ApiException.conflict("FIXTURES_EXIST", "Fixtures already exist. Delete them before changing the draw.");
        }
    }

    /** Removes all fixtures (only while nothing is published), e.g. to redo the group draw. */
    @Transactional
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public void deleteAll() {
        UUID t = tournament.currentTournamentId();
        List<TeamFixture> existing = fixtures.findByTournamentIdOrderByFixtureNumberAsc(t);
        if (existing.stream().anyMatch(f -> f.isPublished() || f.isLocked())) {
            throw ApiException.conflict("FIXTURES_PUBLISHED", "Published fixtures can't be deleted.");
        }
        deleteDrafts(t, existing);
        audit.record(AuditEvent.of("competition.fixtures.deleted", "Tournament", t).inTournament(t)
                .withChange(Map.of("fixtures", existing.size()), null));
    }

    // ---- Scheduling --------------------------------------------------------------------------

    /** Time a fixture occupies: all its matches back to back on its court. */
    static Instant endOf(Instant start, int matchCount, CompetitionSettings s) {
        return start.plus(Duration.ofMinutes((long) matchCount * s.slotMinutes()));
    }

    static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /**
     * Sets (or clears) a fixture's start and court. Matches follow back to back. Refused if the
     * court or either team is already busy then (FIX-009).
     */
    @Transactional
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public void schedule(UUID fixtureId, Instant at, UUID courtId, Long version) {
        UUID t = tournament.currentTournamentId();
        CompetitionSettings s = setup.ensure(t);
        TeamFixture f = own(t, fixtureId, version);
        if (f.isLocked()) {
            throw ApiException.conflict("FIXTURE_LOCKED", "This tie has started; its schedule can't change.");
        }
        if ((at == null) != (courtId == null)) {
            throw ApiException.unprocessable("INCOMPLETE_SCHEDULE", "Choose both a start time and a court, or clear both.");
        }
        if (courtId != null) {
            Court court = courts.findById(courtId).filter(c -> c.getTournamentId().equals(t))
                    .orElseThrow(() -> ApiException.notFound("Court"));
            if (!court.isActive()) {
                throw ApiException.unprocessable("COURT_INACTIVE", court.getName() + " is not in use.");
            }
        }
        List<Match> ms = matches.findByFixtureIdOrderByMatchOrderAsc(f.getId());
        if (at != null) {
            Instant end = endOf(at, ms.size(), s);
            Map<UUID, Integer> counts = matchCounts(t);
            for (TeamFixture other : fixtures.findByTournamentIdOrderByFixtureNumberAsc(t)) {
                if (other.getId().equals(f.getId()) || other.getScheduledAt() == null
                        || other.getStatus() == FixtureStatus.CANCELLED) {
                    continue;
                }
                Instant oEnd = endOf(other.getScheduledAt(), counts.getOrDefault(other.getId(), 0), s);
                if (!overlaps(at, end, other.getScheduledAt(), oEnd)) {
                    continue;
                }
                if (courtId.equals(other.getCourtId())) {
                    throw conflict("The court is taken by tie #" + other.getFixtureNumber() + " at that time.");
                }
                Set<UUID> mine = teamsOf(f);
                if (teamsOf(other).stream().anyMatch(mine::contains)) {
                    throw conflict("A team in this tie plays tie #" + other.getFixtureNumber() + " at that time.");
                }
            }
            // Players already picked must not be busy elsewhere (e.g. a team change after scheduling).
            for (int i = 0; i < ms.size(); i++) {
                Instant ms0 = at.plus(Duration.ofMinutes((long) i * s.slotMinutes()));
                checkPlayersFree(t, s, ms.get(i), lineups.findByMatchId(ms.get(i).getId()).stream()
                        .map(MatchPlayer::getTournamentPlayerId).toList(), ms0);
            }
        }
        Instant before = f.getScheduledAt();
        f.schedule(at, courtId);
        for (int i = 0; i < ms.size(); i++) {
            ms.get(i).schedule(at == null ? null : at.plus(Duration.ofMinutes((long) i * s.slotMinutes())), courtId);
        }
        fixtures.flush();
        audit.record(AuditEvent.of("competition.fixture.scheduled", "TeamFixture", f.getId()).inTournament(t)
                .withChange(mapOf("scheduledAt", before), mapOf("scheduledAt", at, "courtId", courtId)));
    }

    private static ApiException conflict(String detail) {
        return ApiException.conflict("SCHEDULE_CONFLICT", detail);
    }

    private static Set<UUID> teamsOf(TeamFixture f) {
        Set<UUID> s = new HashSet<>();
        if (f.getTeamAId() != null) {
            s.add(f.getTeamAId());
        }
        if (f.getTeamBId() != null) {
            s.add(f.getTeamBId());
        }
        return s;
    }

    private Map<UUID, Integer> matchCounts(UUID t) {
        Map<UUID, Integer> out = new HashMap<>();
        em.createQuery("select m.fixtureId, count(m) from Match m, TeamFixture f where f.id = m.fixtureId "
                + "and f.tournamentId = :t group by m.fixtureId", Object[].class).setParameter("t", t).getResultList()
                .forEach(r -> out.put((UUID) r[0], ((Number) r[1]).intValue()));
        return out;
    }

    // ---- Knockout slots -------------------------------------------------------------------------

    /** Puts teams into a knockout fixture (until results move winners on automatically in M6). */
    @Transactional
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public void setTeams(UUID fixtureId, UUID teamA, UUID teamB, Long version) {
        UUID t = tournament.currentTournamentId();
        TeamFixture f = own(t, fixtureId, version);
        if (f.isLocked()) {
            throw ApiException.conflict("FIXTURE_LOCKED", "This tie has started.");
        }
        Set<UUID> active = teams.activeTeams(t).stream().map(TeamCard::id).collect(Collectors.toSet());
        if ((teamA != null && !active.contains(teamA)) || (teamB != null && !active.contains(teamB))) {
            throw ApiException.unprocessable("INVALID_TEAM", "Choose active teams.");
        }
        if (teamA != null && teamA.equals(teamB)) {
            throw ApiException.unprocessable("SAME_TEAM", "A tie needs two different teams."); // FIX-004
        }
        Map<String, Object> before = mapOf("teamA", f.getTeamAId(), "teamB", f.getTeamBId());
        boolean changedA = !Objects.equals(teamA, f.getTeamAId());
        boolean changedB = !Objects.equals(teamB, f.getTeamBId());
        f.setTeams(teamA, teamB);
        // Line-ups belong to the old team: clear the side that changed.
        for (Match m : matches.findByFixtureIdOrderByMatchOrderAsc(f.getId())) {
            for (MatchPlayer p : lineups.findByMatchId(m.getId())) {
                if ((changedA && "A".equals(p.getSide())) || (changedB && "B".equals(p.getSide()))) {
                    lineups.delete(p);
                }
            }
        }
        fixtures.flush();
        audit.record(AuditEvent.of("competition.fixture.teams_set", "TeamFixture", f.getId()).inTournament(t)
                .withChange(before, mapOf("teamA", teamA, "teamB", teamB)));
    }

    // ---- Line-ups ------------------------------------------------------------------------------

    /**
     * Sets who plays a match (and who scores it). Each side is empty (not decided) or complete.
     * Enforces FIX-008 (roster), the category's gender rule, the per-tie match limit and FIX-009.
     */
    @Transactional
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public void setLineup(UUID fixtureId, UUID matchId, List<UUID> sideA, List<UUID> sideB, UUID scorerUserId,
            Long fixtureVersion) {
        UUID t = tournament.currentTournamentId();
        CompetitionSettings s = setup.ensure(t);
        TeamFixture f = own(t, fixtureId, fixtureVersion);
        Match m = matches.findById(matchId).filter(x -> x.getFixtureId().equals(f.getId()))
                .orElseThrow(() -> ApiException.notFound("Match"));
        if (m.getStatus() != MatchStatus.SCHEDULED) {
            throw ApiException.conflict("MATCH_STARTED", "This match has started; its line-up can't change.");
        }
        MatchCategory cat = categories.findById(m.getCategoryId()).orElseThrow();
        List<UUID> a = sideA == null ? List.of() : sideA;
        List<UUID> b = sideB == null ? List.of() : sideB;
        FieldErrors e = new FieldErrors();
        checkSide(e, "sideA", f.getTeamAId(), a, cat);
        checkSide(e, "sideB", f.getTeamBId(), b, cat);
        if (scorerUserId != null && staff.withAnyPermission(SCORER_PERMISSIONS).stream()
                .noneMatch(x -> x.id().equals(scorerUserId))) {
            e.add("scorerUserId", "Choose an active staff member who can score matches.");
        }
        e.throwIfAny();

        // Per-tie limit
        Map<UUID, Integer> perTie = new HashMap<>();
        for (Match other : matches.findByFixtureIdOrderByMatchOrderAsc(f.getId())) {
            if (other.getId().equals(m.getId())) {
                continue;
            }
            lineups.findByMatchId(other.getId()).forEach(p -> perTie.merge(p.getTournamentPlayerId(), 1, Integer::sum));
        }
        Map<UUID, String> names = names(f);
        for (UUID p : concat(a, b)) {
            if (perTie.getOrDefault(p, 0) + 1 > s.getMaxMatchesPerPlayer()) {
                throw ApiException.unprocessable("PLAYER_LIMIT", names.getOrDefault(p, "A player") + " already plays "
                        + s.getMaxMatchesPerPlayer() + " match" + (s.getMaxMatchesPerPlayer() == 1 ? "" : "es")
                        + " in this tie.");
            }
        }
        if (m.getScheduledAt() != null) {
            checkPlayersFree(t, s, m, concat(a, b), m.getScheduledAt());
        }

        Map<String, Object> before = mapOf("players", lineups.findByMatchId(m.getId()).stream()
                .map(MatchPlayer::getTournamentPlayerId).toList(), "scorer", m.getScorerUserId());
        lineups.deleteByMatch(m.getId());
        lineups.flush();
        for (int i = 0; i < a.size(); i++) {
            lineups.save(new MatchPlayer(m.getId(), "A", i + 1, f.getTeamAId(), a.get(i)));
        }
        for (int i = 0; i < b.size(); i++) {
            lineups.save(new MatchPlayer(m.getId(), "B", i + 1, f.getTeamBId(), b.get(i)));
        }
        m.assignScorer(scorerUserId);
        f.markChanged();
        lineups.flush();
        audit.record(AuditEvent.of("competition.lineup.set", "Match", m.getId()).inTournament(t)
                .withChange(before, mapOf("players", concat(a, b), "scorer", scorerUserId)));
    }

    private void checkSide(FieldErrors e, String field, UUID teamId, List<UUID> players, MatchCategory cat) {
        if (players.isEmpty()) {
            return;
        }
        if (teamId == null) {
            e.add(field, "This side's team isn't decided yet.");
            return;
        }
        if (players.size() != cat.getPlayersPerSide()) {
            e.add(field, cat.getName() + " needs " + cat.getPlayersPerSide() + " player"
                    + (cat.getPlayersPerSide() == 1 ? "" : "s") + " per side.");
            return;
        }
        if (new HashSet<>(players).size() != players.size()) {
            e.add(field, "Pick two different players.");
            return;
        }
        Map<UUID, RosterPlayer> roster = rosterMap(teamId);
        List<String> genders = new ArrayList<>();
        for (UUID p : players) {
            RosterPlayer rp = roster.get(p);
            if (rp == null) {
                e.add(field, "Players must be in the team's squad."); // FIX-008
                return;
            }
            genders.add(rp.gender());
        }
        String problem = genderProblem(cat.getGenderRule(), genders);
        if (problem != null) {
            e.add(field, problem);
        }
    }

    /** Null if the genders suit the category, else a message. Unknown gender fails gendered categories. */
    static String genderProblem(GenderRule rule, List<String> genders) {
        return switch (rule) {
            case ANY -> null;
            case MALE -> genders.stream().allMatch("MALE"::equals) ? null : "This category is for men only.";
            case FEMALE -> genders.stream().allMatch("FEMALE"::equals) ? null : "This category is for women only.";
            case MIXED -> genders.size() == 2 && genders.contains("MALE") && genders.contains("FEMALE") ? null
                    : "Mixed doubles needs one man and one woman.";
        };
    }

    /** FIX-009: none of these players may be in another match whose time overlaps this one. */
    private void checkPlayersFree(UUID t, CompetitionSettings s, Match m, List<UUID> players, Instant start) {
        if (players.isEmpty() || start == null) {
            return;
        }
        Instant end = start.plus(Duration.ofMinutes(s.slotMinutes()));
        List<MatchPlayer> elsewhere = lineups.findByPlayers(players).stream()
                .filter(p -> !p.getMatchId().equals(m.getId())).toList();
        // Matches of the same tie run back to back, so only other ties can clash.
        if (elsewhere.isEmpty()) {
            return;
        }
        Map<UUID, Match> others = matches.findAllById(elsewhere.stream().map(MatchPlayer::getMatchId).toList()).stream()
                .collect(Collectors.toMap(Match::getId, Function.identity()));
        for (MatchPlayer p : elsewhere) {
            Match o = others.get(p.getMatchId());
            if (o == null || o.getFixtureId().equals(m.getFixtureId()) || o.getScheduledAt() == null
                    || o.getStatus() == MatchStatus.CANCELLED) {
                continue;
            }
            Instant oEnd = o.getScheduledAt().plus(Duration.ofMinutes(s.slotMinutes()));
            if (overlaps(start, end, o.getScheduledAt(), oEnd)) {
                TeamFixture of = fixtures.findById(o.getFixtureId()).orElseThrow();
                throw ApiException.conflict("PLAYER_BUSY", "A player is already in match " + o.getMatchOrder()
                        + " of tie #" + of.getFixtureNumber() + " at that time.");
            }
        }
    }

    private Map<UUID, RosterPlayer> rosterMap(UUID teamId) {
        RosterSource src = rosters.getIfAvailable();
        if (src == null) {
            return Map.of();
        }
        return src.roster(teamId).stream().collect(Collectors.toMap(RosterPlayer::entryId, Function.identity()));
    }

    private Map<UUID, String> names(TeamFixture f) {
        Map<UUID, String> out = new HashMap<>();
        for (UUID team : teamsOf(f)) {
            rosterMap(team).values().forEach(p -> out.put(p.entryId(), p.fullName()));
        }
        return out;
    }

    private static List<UUID> concat(List<UUID> a, List<UUID> b) {
        List<UUID> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    // ---- Publishing ----------------------------------------------------------------------------

    /** FIX-010: puts new and changed fixtures on the public site. Null ids = everything pending. */
    @Transactional
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public int publish(Collection<UUID> ids) {
        UUID t = tournament.currentTournamentId();
        Instant now = clock.instant();
        Set<UUID> wanted = ids == null || ids.isEmpty() ? null : new HashSet<>(ids);
        int count = 0;
        for (TeamFixture f : fixtures.findByTournamentIdOrderByFixtureNumberAsc(t)) {
            boolean pending = !f.isPublished() || f.isChangedSincePublish();
            if (pending && (wanted == null || wanted.contains(f.getId()))) {
                f.publish(now);
                f.freeze(read.snapshot(f));
                count++;
            }
        }
        if (count == 0) {
            throw ApiException.conflict("NOTHING_TO_PUBLISH", "Everything is already published.");
        }
        fixtures.flush();
        audit.record(AuditEvent.of("competition.fixtures.published", "Tournament", t).inTournament(t)
                .withChange(null, Map.of("fixtures", count)));
        site.touch(SiteRevalidator.FIXTURES);
        return count;
    }

    // ---- Helpers -------------------------------------------------------------------------------

    private TeamFixture own(UUID t, UUID id, Long version) {
        TeamFixture f = fixtures.findById(id).filter(x -> x.getTournamentId().equals(t))
                .orElseThrow(() -> ApiException.notFound("Fixture"));
        if (version != null && !Objects.equals(version, f.getVersion())) {
            throw ApiException.conflict("STALE_DATA", "This tie was changed by someone else. Reload and try again.");
        }
        return f;
    }

    static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
