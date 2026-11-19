package lk.ngbcl.competition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.ngbcl.competition.domain.CompetitionGroup;
import lk.ngbcl.competition.domain.CompetitionGroupRepository;
import lk.ngbcl.competition.domain.CompetitionSettings;
import lk.ngbcl.competition.domain.CompetitionSettingsRepository;
import lk.ngbcl.competition.domain.Court;
import lk.ngbcl.competition.domain.CourtRepository;
import lk.ngbcl.competition.domain.GroupTeam;
import lk.ngbcl.competition.domain.GroupTeamRepository;
import lk.ngbcl.competition.domain.Match;
import lk.ngbcl.competition.domain.MatchCategory;
import lk.ngbcl.competition.domain.MatchGame;
import lk.ngbcl.competition.domain.MatchGameRepository;
import lk.ngbcl.competition.domain.MatchCategoryRepository;
import lk.ngbcl.competition.domain.MatchPlayer;
import lk.ngbcl.competition.domain.MatchPlayerRepository;
import lk.ngbcl.competition.domain.MatchRepository;
import lk.ngbcl.competition.domain.Stage;
import lk.ngbcl.competition.domain.StageRepository;
import lk.ngbcl.competition.domain.TeamFixture;
import lk.ngbcl.competition.domain.TeamFixtureRepository;
import lk.ngbcl.competition.service.CompetitionDtos.CourtView;
import lk.ngbcl.competition.service.CompetitionDtos.FixtureView;
import lk.ngbcl.competition.service.CompetitionDtos.FixturesView;
import lk.ngbcl.competition.service.CompetitionDtos.GroupView;
import lk.ngbcl.competition.service.CompetitionDtos.GroupsView;
import lk.ngbcl.competition.service.CompetitionDtos.MatchView;
import lk.ngbcl.competition.service.CompetitionDtos.PlayerRef;
import lk.ngbcl.competition.service.CompetitionDtos.PublicFixture;
import lk.ngbcl.competition.service.CompetitionDtos.PublicFixtures;
import lk.ngbcl.competition.service.CompetitionDtos.PublicGroup;
import lk.ngbcl.competition.service.CompetitionDtos.PublicMatch;
import lk.ngbcl.competition.service.CompetitionDtos.PublicPlayerRef;
import lk.ngbcl.competition.service.CompetitionDtos.PublicTeamRef;
import lk.ngbcl.competition.service.CompetitionDtos.PublishItem;
import lk.ngbcl.competition.service.CompetitionDtos.PublishPreview;
import lk.ngbcl.competition.service.CompetitionDtos.RosterView;
import lk.ngbcl.competition.service.CompetitionDtos.StaffRef;
import lk.ngbcl.competition.service.CompetitionDtos.StageView;
import lk.ngbcl.competition.service.CompetitionDtos.TeamRef;
import lk.ngbcl.competition.service.RosterSource.RosterPlayer;
import lk.ngbcl.identity.service.StaffDirectory;
import lk.ngbcl.player.service.PlayerService;
import lk.ngbcl.player.service.PlayerService.LotPlayer;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.team.service.TeamService;
import lk.ngbcl.team.service.TeamService.TeamCard;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds the admin fixture views, the public snapshot of a fixture, and the public fixture list. */
@Component
public class CompetitionReadModel {

    private final CompetitionSettingsRepository settings;
    private final StageRepository stages;
    private final CompetitionGroupRepository groups;
    private final GroupTeamRepository groupTeams;
    private final TeamFixtureRepository fixtures;
    private final MatchRepository matches;
    private final MatchPlayerRepository lineups;
    private final MatchGameRepository games;
    private final MatchCategoryRepository categories;
    private final CourtRepository courts;
    private final TeamService teams;
    private final PlayerService players;
    private final ObjectProvider<RosterSource> rosters;
    private final StaffDirectory staff;
    private final TournamentService tournament;
    private final ObjectMapper json;

    public CompetitionReadModel(CompetitionSettingsRepository settings, StageRepository stages,
            CompetitionGroupRepository groups, GroupTeamRepository groupTeams, TeamFixtureRepository fixtures,
            MatchRepository matches, MatchPlayerRepository lineups, MatchGameRepository games,
            MatchCategoryRepository categories, CourtRepository courts, TeamService teams, PlayerService players, ObjectProvider<RosterSource> rosters,
            StaffDirectory staff, TournamentService tournament, ObjectMapper json) {
        this.settings = settings;
        this.stages = stages;
        this.groups = groups;
        this.groupTeams = groupTeams;
        this.fixtures = fixtures;
        this.matches = matches;
        this.lineups = lineups;
        this.games = games;
        this.categories = categories;
        this.courts = courts;
        this.teams = teams;
        this.players = players;
        this.rosters = rosters;
        this.staff = staff;
        this.tournament = tournament;
        this.json = json;
    }

    /** Everything a view of the competition needs, loaded once. */
    private final class Ctx {
        final UUID t;
        final CompetitionSettings settings;
        final Map<UUID, Stage> stageById;
        final List<CompetitionGroup> groupList;
        final Map<UUID, List<GroupTeam>> groupMembers;
        final List<TeamFixture> fixtureList;
        final Map<UUID, List<Match>> matchesByFixture;
        final Map<UUID, List<MatchPlayer>> lineupByMatch;
        final Map<UUID, List<String>> scoresByMatch = new HashMap<>();
        final Map<UUID, MatchCategory> categoryById;
        final Map<UUID, Court> courtById;
        final Map<UUID, TeamCard> cards;
        final Map<UUID, LotPlayer> people;
        final Map<UUID, StaffRef> staffById;

        Ctx(UUID t, List<TeamFixture> only) {
            this.t = t;
            this.settings = CompetitionReadModel.this.settings.findById(t).orElse(null);
            this.stageById = stages.findByTournamentIdOrderByStageOrderAsc(t).stream()
                    .collect(Collectors.toMap(Stage::getId, Function.identity()));
            this.groupList = groups.findByTournamentIdOrderByGroupOrderAsc(t);
            this.groupMembers = groupList.isEmpty() ? Map.of()
                    : groupTeams.findByGroupIdIn(groupList.stream().map(CompetitionGroup::getId).toList()).stream()
                            .collect(Collectors.groupingBy(GroupTeam::getGroupId));
            this.fixtureList = only != null ? only : fixtures.findByTournamentIdOrderByFixtureNumberAsc(t);
            List<Match> ms = fixtureList.isEmpty() ? List.of()
                    : matches.findByFixtureIdInOrderByMatchOrderAsc(fixtureList.stream().map(TeamFixture::getId).toList());
            this.matchesByFixture = ms.stream().collect(Collectors.groupingBy(Match::getFixtureId));
            List<MatchPlayer> lps = ms.isEmpty() ? List.of() : lineups.findByMatchIdIn(ms.stream().map(Match::getId).toList());
            this.lineupByMatch = lps.stream().collect(Collectors.groupingBy(MatchPlayer::getMatchId));
            if (!ms.isEmpty()) {
                games.findByMatchIdIn(ms.stream().map(Match::getId).toList()).stream()
                        .sorted(Comparator.comparingInt(MatchGame::getGameNumber))
                        .forEach(g -> scoresByMatch.computeIfAbsent(g.getMatchId(), k -> new ArrayList<>())
                                .add(g.getScoreA() + "-" + g.getScoreB()));
            }
            this.categoryById = categories.findByTournamentIdOrderByDisplayOrderAscCodeAsc(t).stream()
                    .collect(Collectors.toMap(MatchCategory::getId, Function.identity()));
            this.courtById = courts.findByTournamentIdOrderByDisplayOrderAscNameAsc(t).stream()
                    .collect(Collectors.toMap(Court::getId, Function.identity()));
            this.cards = teams.cards(t);
            this.people = players.lotPlayers(lps.stream().map(MatchPlayer::getTournamentPlayerId).distinct().toList());
            this.staffById = new HashMap<>();
            staff.withAnyPermission(FixtureService.SCORER_PERMISSIONS)
                    .forEach(x -> staffById.put(x.id(), new StaffRef(x.id(), x.displayName())));
        }

        TeamRef team(UUID id) {
            TeamCard c = id == null ? null : cards.get(id);
            return c == null ? null : new TeamRef(c.id(), c.slug(), c.name(), c.shortCode(), c.colorSlot(), c.logoUrl());
        }

        PublicTeamRef publicTeam(UUID id) {
            TeamCard c = id == null ? null : cards.get(id);
            return c == null ? null : new PublicTeamRef(c.slug(), c.name(), c.shortCode(), c.colorSlot(), c.logoUrl());
        }

        String groupName(UUID id) {
            return id == null ? null : groupList.stream().filter(g -> g.getId().equals(id)).map(CompetitionGroup::getName)
                    .findFirst().orElse(null);
        }

        int slotMinutes() {
            return settings == null ? 40 : settings.slotMinutes();
        }
    }

    // ---- Admin ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public FixturesView fixtures() {
        UUID t = tournament.currentTournamentId();
        Ctx c = new Ctx(t, null);
        List<FixtureView> list = c.fixtureList.stream().map(f -> fixtureView(c, f)).toList();
        List<StageView> stageViews = c.stageById.values().stream()
                .sorted(Comparator.comparingInt(Stage::getStageOrder))
                .map(s -> new StageView(s.getId(), s.getName(), s.getType(), s.getStageOrder())).toList();
        List<CourtView> courtViews = c.courtById.values().stream()
                .sorted(Comparator.comparingInt(Court::getDisplayOrder).thenComparing(Court::getName))
                .map(x -> new CourtView(x.getId(), x.getName(), x.isActive(), x.getVersion())).toList();
        List<StaffRef> scorers = c.staffById.values().stream()
                .sorted(Comparator.comparing(StaffRef::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
        int unpublished = (int) c.fixtureList.stream().filter(f -> !f.isPublished()).count();
        int changed = (int) c.fixtureList.stream().filter(f -> f.isPublished() && f.isChangedSincePublish()).count();
        return new FixturesView(stageViews, groupViews(c), list, courtViews, scorers,
                c.settings == null ? 40 : c.settings.getMatchMinutes(), c.settings == null ? 0 : c.settings.getRestMinutes(),
                unpublished, changed);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public FixtureView fixture(UUID id) {
        UUID t = tournament.currentTournamentId();
        TeamFixture f = fixtures.findById(id).filter(x -> x.getTournamentId().equals(t))
                .orElseThrow(() -> ApiException.notFound("Fixture"));
        return fixtureView(new Ctx(t, List.of(f)), f);
    }

    private List<GroupView> groupViews(Ctx c) {
        return c.groupList.stream().map(g -> new GroupView(g.getId(), g.getName(),
                c.groupMembers.getOrDefault(g.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(GroupTeam::getSeed))
                        .map(gt -> c.team(gt.getTeamId())).filter(x -> x != null).toList()))
                .toList();
    }

    private FixtureView fixtureView(Ctx c, TeamFixture f) {
        Stage st = c.stageById.get(f.getStageId());
        List<Match> ms = c.matchesByFixture.getOrDefault(f.getId(), List.of());
        List<MatchView> mvs = ms.stream().map(m -> matchView(c, m)).toList();
        List<String> warnings = new ArrayList<>();
        if (f.getScheduledAt() == null) {
            warnings.add("Not scheduled");
        }
        if (f.getTeamAId() == null || f.getTeamBId() == null) {
            warnings.add("Teams to be decided");
        }
        boolean complete = mvs.stream().allMatch(MatchView::lineupComplete);
        if (!complete && f.getTeamAId() != null && f.getTeamBId() != null) {
            warnings.add("Line-ups incomplete");
        }
        if (mvs.stream().anyMatch(m -> m.scorer() == null)) {
            warnings.add("Scorer not assigned");
        }
        Instant end = f.getScheduledAt() == null || c.settings == null ? null
                : FixtureService.endOf(f.getScheduledAt(), ms.size(), c.settings);
        Court court = f.getCourtId() == null ? null : c.courtById.get(f.getCourtId());
        return new FixtureView(f.getId(), f.getFixtureNumber(), f.getStageId(), st == null ? null : st.getName(),
                st == null ? null : st.getType(), f.getGroupId(), c.groupName(f.getGroupId()), f.getRoundNumber(),
                f.getRoundName(), f.getBracketPosition(), c.team(f.getTeamAId()), c.team(f.getTeamBId()), f.getSourceA(),
                f.getSourceB(), f.getScheduledAt(), end, f.getCourtId(), court == null ? null : court.getName(),
                f.getStatus(), f.isPublished(), f.isChangedSincePublish(), f.getWinnerToFixtureId(), f.getWinnerToSide(),
                f.getLoserToFixtureId(), f.getLoserToSide(), mvs, complete, warnings, f.getVersion(), f.getMatchesWonA(),
                f.getMatchesWonB(), f.getResult());
    }

    private MatchView matchView(Ctx c, Match m) {
        MatchCategory cat = c.categoryById.get(m.getCategoryId());
        List<MatchPlayer> lp = c.lineupByMatch.getOrDefault(m.getId(), List.of());
        List<PlayerRef> a = side(c, lp, "A");
        List<PlayerRef> b = side(c, lp, "B");
        boolean complete = a.size() == m.getPlayersPerSide() && b.size() == m.getPlayersPerSide();
        return new MatchView(m.getId(), m.getMatchOrder(), m.getCategoryId(), cat == null ? null : cat.getCode(),
                cat == null ? null : cat.getName(), m.getPlayersPerSide(), cat == null ? null : cat.getGenderRule(),
                m.getScheduledAt(), a, b, m.getScorerUserId() == null ? null : c.staffById.getOrDefault(m.getScorerUserId(),
                        new StaffRef(m.getScorerUserId(), "Former scorer")),
                m.getStatus(), complete, m.getPointsToWin(), m.getWinBy(), m.getPointCap(), m.getGamesToWin(),
                m.isRulesCustom(), m.getGamesA(), m.getGamesB(), m.getWinnerSide(), m.getOutcome(),
                c.scoresByMatch.getOrDefault(m.getId(), List.of()));
    }

    private List<PlayerRef> side(Ctx c, List<MatchPlayer> lp, String side) {
        return lp.stream().filter(p -> side.equals(p.getSide())).sorted(Comparator.comparingInt(MatchPlayer::getPosition))
                .map(p -> {
                    LotPlayer x = c.people.get(p.getTournamentPlayerId());
                    return new PlayerRef(p.getTournamentPlayerId(), x == null ? null : x.slug(),
                            x == null ? "Unknown player" : x.fullName(), x == null ? null : x.gender());
                }).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public GroupsView groups() {
        UUID t = tournament.currentTournamentId();
        Ctx c = new Ctx(t, List.of());
        List<GroupView> gv = groupViews(c);
        Set<UUID> drawn = gv.stream().flatMap(g -> g.teams().stream()).map(TeamRef::id).collect(Collectors.toSet());
        List<TeamRef> unassigned = teams.activeTeams(t).stream().filter(x -> !drawn.contains(x.id()))
                .map(x -> new TeamRef(x.id(), x.slug(), x.name(), x.shortCode(), x.colorSlot(), x.logoUrl())).toList();
        boolean locked = !fixtures.findByTournamentIdOrderByFixtureNumberAsc(t).isEmpty();
        return new GroupsView(gv, unassigned, c.settings == null ? 2 : c.settings.getGroupCount(), locked);
    }

    /** Both squads for the line-up pickers. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public List<RosterView> rosters(UUID fixtureId) {
        UUID t = tournament.currentTournamentId();
        TeamFixture f = fixtures.findById(fixtureId).filter(x -> x.getTournamentId().equals(t))
                .orElseThrow(() -> ApiException.notFound("Fixture"));
        Map<UUID, TeamCard> cards = teams.cards(t);
        RosterSource src = rosters.getIfAvailable();
        List<RosterView> out = new ArrayList<>();
        for (UUID id : new UUID[] {f.getTeamAId(), f.getTeamBId()}) {
            if (id == null) {
                out.add(null);
                continue;
            }
            TeamCard card = cards.get(id);
            List<RosterPlayer> squad = src == null ? List.of() : src.roster(id);
            out.add(new RosterView(new TeamRef(card.id(), card.slug(), card.name(), card.shortCode(), card.colorSlot(),
                    card.logoUrl()), squad.stream().map(p -> new PlayerRef(p.entryId(), p.slug(), p.fullName(), p.gender()))
                    .toList()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public PublishPreview publishPreview() {
        UUID t = tournament.currentTournamentId();
        Ctx c = new Ctx(t, null);
        List<PublishItem> items = new ArrayList<>();
        int unscheduled = 0;
        for (TeamFixture f : c.fixtureList) {
            boolean isNew = !f.isPublished();
            if (!isNew && !f.isChangedSincePublish()) {
                continue;
            }
            if (f.getScheduledAt() == null) {
                unscheduled++;
            }
            items.add(new PublishItem(f.getId(), f.getFixtureNumber(), label(c, f), isNew ? "NEW" : "CHANGED"));
        }
        return new PublishPreview(items, unscheduled);
    }

    private String label(Ctx c, TeamFixture f) {
        TeamRef a = c.team(f.getTeamAId());
        TeamRef b = c.team(f.getTeamBId());
        return f.getRoundName() + ": " + (a != null ? a.name() : f.getSourceA() != null ? f.getSourceA() : "TBD") + " v "
                + (b != null ? b.name() : f.getSourceB() != null ? f.getSourceB() : "TBD");
    }

    // ---- Public ---------------------------------------------------------------------------------

    /** JSON frozen into the fixture when it is published. */
    @Transactional(readOnly = true)
    public String snapshot(TeamFixture f) {
        Ctx c = new Ctx(f.getTournamentId(), List.of(f));
        Stage st = c.stageById.get(f.getStageId());
        Court court = f.getCourtId() == null ? null : c.courtById.get(f.getCourtId());
        Integer winnerTo = f.getWinnerToFixtureId() == null ? null
                : fixtures.findById(f.getWinnerToFixtureId()).map(TeamFixture::getFixtureNumber).orElse(null);
        List<PublicMatch> pm = c.matchesByFixture.getOrDefault(f.getId(), List.of()).stream().map(m -> {
            MatchCategory cat = c.categoryById.get(m.getCategoryId());
            List<MatchPlayer> lp = c.lineupByMatch.getOrDefault(m.getId(), List.of());
            return new PublicMatch(m.getMatchOrder(), cat == null ? null : cat.getCode(), cat == null ? null : cat.getName(),
                    publicSide(c, lp, "A"), publicSide(c, lp, "B"), m.getScheduledAt(), m.getStatus(),
                    c.scoresByMatch.getOrDefault(m.getId(), List.of()), m.getWinnerSide(), m.getOutcome(),
                    m.getStatus() == lk.ngbcl.competition.domain.MatchStatus.SUBMITTED);
        }).toList();
        PublicFixture p = new PublicFixture(f.getFixtureNumber(), st == null ? null : st.getName(),
                st == null ? null : st.getType(), c.groupName(f.getGroupId()), f.getRoundNumber(), f.getRoundName(),
                f.getBracketPosition(), c.publicTeam(f.getTeamAId()), c.publicTeam(f.getTeamBId()), f.getSourceA(),
                f.getSourceB(), f.getScheduledAt(), court == null ? null : court.getName(), f.getStatus(), winnerTo, pm,
                f.getMatchesWonA(), f.getMatchesWonB(), f.getResult());
        try {
            return json.writeValueAsString(p);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<PublicPlayerRef> publicSide(Ctx c, List<MatchPlayer> lp, String side) {
        return lp.stream().filter(p -> side.equals(p.getSide())).sorted(Comparator.comparingInt(MatchPlayer::getPosition))
                .map(p -> {
                    LotPlayer x = c.people.get(p.getTournamentPlayerId());
                    return new PublicPlayerRef(x == null ? null : x.slug(), x == null ? "Player" : x.fullName());
                }).toList();
    }

    /** Published fixtures as they were when published (FIX-010), plus the group draw. */
    @Transactional(readOnly = true)
    public PublicFixtures publicFixtures() {
        UUID t = tournament.currentTournamentId();
        CompetitionSettings s = settings.findById(t).orElse(null);
        List<PublicFixture> list = new ArrayList<>();
        boolean anyPublished = false;
        for (TeamFixture f : fixtures.findByTournamentIdOrderByFixtureNumberAsc(t)) {
            if (f.isPublished() && f.getPublishedSnapshot() != null) {
                anyPublished = true;
                try {
                    list.add(json.readValue(f.getPublishedSnapshot(), PublicFixture.class));
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        List<PublicGroup> pg = List.of();
        if (anyPublished) {
            Ctx c = new Ctx(t, List.of());
            pg = c.groupList.stream().map(g -> new PublicGroup(g.getName(),
                    c.groupMembers.getOrDefault(g.getId(), List.of()).stream()
                            .sorted(Comparator.comparingInt(GroupTeam::getSeed))
                            .map(gt -> c.publicTeam(gt.getTeamId())).filter(x -> x != null).toList()))
                    .toList();
        }
        list.sort(Comparator.comparing(PublicFixture::scheduledAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(PublicFixture::number));
        return new PublicFixtures(s == null ? null : s.getFormat(), pg, list);
    }
}
