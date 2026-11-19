package lk.ngbcl.scoring.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.ngbcl.competition.domain.CompetitionGroup;
import lk.ngbcl.competition.domain.CompetitionGroupRepository;
import lk.ngbcl.competition.domain.Court;
import lk.ngbcl.competition.domain.CourtRepository;
import lk.ngbcl.competition.domain.Match;
import lk.ngbcl.competition.domain.MatchCategory;
import lk.ngbcl.competition.domain.MatchCategoryRepository;
import lk.ngbcl.competition.domain.MatchGame;
import lk.ngbcl.competition.domain.MatchGameRepository;
import lk.ngbcl.competition.domain.MatchPlayer;
import lk.ngbcl.competition.domain.MatchPlayerRepository;
import lk.ngbcl.competition.domain.MatchRepository;
import lk.ngbcl.competition.domain.MatchStatus;
import lk.ngbcl.competition.domain.TeamFixture;
import lk.ngbcl.competition.domain.TeamFixtureRepository;
import lk.ngbcl.identity.service.StaffDirectory;
import lk.ngbcl.player.service.PlayerService;
import lk.ngbcl.player.service.PlayerService.LotPlayer;
import lk.ngbcl.scoring.domain.ScoreEvent;
import lk.ngbcl.scoring.domain.ScoreEventRepository;
import lk.ngbcl.scoring.domain.ScoringSettings;
import lk.ngbcl.scoring.service.ScoringDtos.GameView;
import lk.ngbcl.scoring.service.ScoringDtos.MatchState;
import lk.ngbcl.scoring.service.ScoringDtos.PlayerName;
import lk.ngbcl.scoring.service.ScoringDtos.PublicLive;
import lk.ngbcl.scoring.service.ScoringDtos.PublicLiveMatch;
import lk.ngbcl.scoring.service.ScoringDtos.RulesView;
import lk.ngbcl.scoring.service.ScoringDtos.ScorerCard;
import lk.ngbcl.scoring.service.ScoringDtos.TeamLite;
import lk.ngbcl.security.Permission;
import lk.ngbcl.team.service.TeamService;
import lk.ngbcl.team.service.TeamService.TeamCard;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds scorer, results-queue and public live views of matches. */
@Component
public class ScoringReadModel {

    private static final Duration RECENT = Duration.ofHours(3);

    private final MatchRepository matches;
    private final TeamFixtureRepository fixtures;
    private final MatchGameRepository games;
    private final ScoreEventRepository events;
    private final MatchPlayerRepository lineups;
    private final MatchCategoryRepository categories;
    private final CourtRepository courts;
    private final CompetitionGroupRepository groups;
    private final TeamService teams;
    private final PlayerService players;
    private final StaffDirectory staff;
    private final ScoringSettingsService settings;
    private final TournamentService tournament;

    public ScoringReadModel(MatchRepository matches, TeamFixtureRepository fixtures, MatchGameRepository games,
            ScoreEventRepository events, MatchPlayerRepository lineups, MatchCategoryRepository categories,
            CourtRepository courts, CompetitionGroupRepository groups, TeamService teams, PlayerService players,
            StaffDirectory staff, ScoringSettingsService settings, TournamentService tournament) {
        this.matches = matches;
        this.fixtures = fixtures;
        this.games = games;
        this.events = events;
        this.lineups = lineups;
        this.categories = categories;
        this.courts = courts;
        this.groups = groups;
        this.teams = teams;
        this.players = players;
        this.staff = staff;
        this.settings = settings;
        this.tournament = tournament;
    }

    /** Lookups shared by every view in one request. */
    private final class Ctx {
        final UUID t;
        final ScoringSettings s;
        final Map<UUID, TeamFixture> fixtureById = new HashMap<>();
        final Map<UUID, MatchCategory> categoryById;
        final Map<UUID, Court> courtById;
        final Map<UUID, String> groupNames;
        final Map<UUID, TeamCard> cards;
        final Map<UUID, List<MatchPlayer>> lineupByMatch;
        final Map<UUID, List<MatchGame>> gamesByMatch;
        final Map<UUID, LotPlayer> people;
        final Map<UUID, String> staffNames = new HashMap<>();

        Ctx(UUID t, Collection<Match> ms) {
            this.t = t;
            this.s = settings.peek(t);
            List<UUID> fids = ms.stream().map(Match::getFixtureId).distinct().toList();
            fixtures.findAllById(fids).forEach(f -> fixtureById.put(f.getId(), f));
            this.categoryById = categories.findByTournamentIdOrderByDisplayOrderAscCodeAsc(t).stream()
                    .collect(Collectors.toMap(MatchCategory::getId, Function.identity()));
            this.courtById = courts.findByTournamentIdOrderByDisplayOrderAscNameAsc(t).stream()
                    .collect(Collectors.toMap(Court::getId, Function.identity()));
            this.groupNames = groups.findByTournamentIdOrderByGroupOrderAsc(t).stream()
                    .collect(Collectors.toMap(CompetitionGroup::getId, CompetitionGroup::getName));
            this.cards = teams.cards(t);
            List<UUID> mids = ms.stream().map(Match::getId).toList();
            List<MatchPlayer> lps = mids.isEmpty() ? List.of() : lineups.findByMatchIdIn(mids);
            this.lineupByMatch = lps.stream().collect(Collectors.groupingBy(MatchPlayer::getMatchId));
            this.gamesByMatch = (mids.isEmpty() ? List.<MatchGame>of() : games.findByMatchIdIn(mids)).stream()
                    .collect(Collectors.groupingBy(MatchGame::getMatchId));
            this.people = players.lotPlayers(lps.stream().map(MatchPlayer::getTournamentPlayerId).distinct().toList());
            staff.withAnyPermission(java.util.Set.of(Permission.MATCH_SCORE, Permission.MATCH_SCORE_ANY, Permission.RESULT_CONFIRM))
                    .forEach(x -> staffNames.put(x.id(), x.displayName()));
        }

        TeamLite team(UUID id) {
            TeamCard c = id == null ? null : cards.get(id);
            return c == null ? null : new TeamLite(c.slug(), c.name(), c.shortCode(), c.colorSlot(), c.logoUrl());
        }

        List<PlayerName> side(Match m, String side) {
            return lineupByMatch.getOrDefault(m.getId(), List.of()).stream().filter(p -> side.equals(p.getSide()))
                    .sorted(Comparator.comparingInt(MatchPlayer::getPosition)).map(p -> {
                        LotPlayer x = people.get(p.getTournamentPlayerId());
                        return new PlayerName(x == null ? null : x.slug(), x == null ? "Player" : x.fullName());
                    }).toList();
        }

        List<GameView> gameViews(Match m) {
            return gamesByMatch.getOrDefault(m.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(MatchGame::getGameNumber))
                    .map(g -> new GameView(g.getGameNumber(), g.getScoreA(), g.getScoreB(), g.getWinnerSide())).toList();
        }
    }

    private Ctx ctx(Collection<Match> ms) {
        return new Ctx(tournament.currentTournamentId(), ms);
    }

    // ---- Views ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MatchState state(UUID matchId) {
        Match m = matches.findById(matchId).orElseThrow(() -> lk.ngbcl.shared.web.ApiException.notFound("Match"));
        return state(ctx(List.of(m)), m);
    }

    private MatchState state(Ctx c, Match m) {
        TeamFixture f = c.fixtureById.get(m.getFixtureId());
        MatchCategory cat = c.categoryById.get(m.getCategoryId());
        ScoringRules r = settings.rulesFor(m, c.s);
        List<GameView> gv = c.gameViews(m);
        GameView cur = gv.isEmpty() ? null : gv.get(gv.size() - 1);
        boolean inPlay = cur != null && cur.winnerSide() == null && m.getWinnerSide() == null;
        List<ScoreEvent> evs = events.findByMatchIdOrderBySeqAsc(m.getId());
        String lastPoint = null;
        boolean undoable = false;
        for (ScoreEvent e : evs) {
            if (e.isVoided()) {
                continue;
            }
            if ("POINT".equals(e.getType())) {
                lastPoint = e.getSide();
            }
            if (List.of("POINT", "MANUAL", "WALKOVER", "RETIRED").contains(e.getType())) {
                undoable = true;
            }
        }
        Court court = m.getCourtId() == null ? null : c.courtById.get(m.getCourtId());
        int lineupA = c.side(m, "A").size();
        int lineupB = c.side(m, "B").size();
        return new MatchState(m.getId(), f.getId(), f.getFixtureNumber(), f.getRoundName(),
                f.getGroupId() == null ? null : c.groupNames.get(f.getGroupId()), m.getMatchOrder(),
                cat == null ? null : cat.getCode(), cat == null ? null : cat.getName(), c.team(f.getTeamAId()),
                c.team(f.getTeamBId()), c.side(m, "A"), c.side(m, "B"), court == null ? null : court.getName(),
                m.getScheduledAt(), m.getStatus(),
                new RulesView(r.pointsToWin(), r.winBy(), r.cap(), r.gamesToWin(), m.isRulesCustom()), gv,
                inPlay ? cur.number() : null, inPlay ? cur.scoreA() : 0, inPlay ? cur.scoreB() : 0, m.getGamesA(),
                m.getGamesB(), m.getFirstServer() == null ? null : ScoringRules.server(m.getFirstServer(), lastPoint),
                m.getFirstServer(), m.getWinnerSide(), m.getOutcome(), m.getOutcomeNote(), m.getStateVersion(),
                m.getStatus() == MatchStatus.LIVE && undoable,
                lineupA == m.getPlayersPerSide() && lineupB == m.getPlayersPerSide(), m.getReturnedReason(),
                c.s.isTwoStepApproval(), c.s.isAllowWalkover(), c.s.isAllowRetirement(), m.getSubmittedAt(),
                m.getSubmittedBy() == null ? null : c.staffNames.getOrDefault(m.getSubmittedBy(), "Scorer"),
                m.getScorerUserId() == null ? null : c.staffNames.get(m.getScorerUserId()));
    }

    /** The scorer's matches: assigned to them, or every match for officials who may score any. */
    @Transactional(readOnly = true)
    public List<ScorerCard> scorerMatches(UUID userId, boolean any) {
        UUID t = tournament.currentTournamentId();
        List<Match> ms;
        if (any) {
            List<UUID> fids = fixtures.findByTournamentIdOrderByFixtureNumberAsc(t).stream().map(TeamFixture::getId).toList();
            ms = fids.isEmpty() ? List.of() : matches.findByFixtureIdInOrderByMatchOrderAsc(fids);
        } else {
            ms = matches.findByScorerUserIdOrderByScheduledAtAsc(userId);
        }
        Ctx c = ctx(ms);
        return ms.stream().filter(m -> {
            TeamFixture f = c.fixtureById.get(m.getFixtureId());
            return f != null && f.getTournamentId().equals(t) && f.getTeamAId() != null && f.getTeamBId() != null;
        }).map(m -> {
            TeamFixture f = c.fixtureById.get(m.getFixtureId());
            MatchCategory cat = c.categoryById.get(m.getCategoryId());
            Court court = m.getCourtId() == null ? null : c.courtById.get(m.getCourtId());
            List<PlayerName> a = c.side(m, "A");
            List<PlayerName> b = c.side(m, "B");
            return new ScorerCard(m.getId(), f.getId(), f.getFixtureNumber(), f.getRoundName(),
                    f.getGroupId() == null ? null : c.groupNames.get(f.getGroupId()), m.getMatchOrder(),
                    cat == null ? null : cat.getCode(), cat == null ? null : cat.getName(), c.team(f.getTeamAId()),
                    c.team(f.getTeamBId()), a, b, m.getScheduledAt(), court == null ? null : court.getName(), m.getStatus(),
                    a.size() == m.getPlayersPerSide() && b.size() == m.getPlayersPerSide(), m.getGamesA(), m.getGamesB(),
                    m.getReturnedReason());
        }).sorted(Comparator.comparing(ScorerCard::scheduledAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(ScorerCard::fixtureNumber).thenComparingInt(ScorerCard::order)).toList();
    }

    /** Results waiting for an official (two-step approval). */
    @Transactional(readOnly = true)
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('RESULT_CONFIRM', 'RESULT_CORRECT')")
    public List<MatchState> pending() {
        UUID t = tournament.currentTournamentId();
        List<Match> ms = matches.findByStatusIn(List.of(MatchStatus.SUBMITTED)).stream()
                .filter(m -> fixtures.findById(m.getFixtureId()).map(f -> f.getTournamentId().equals(t)).orElse(false))
                .toList();
        Ctx c = ctx(ms);
        return ms.stream().map(m -> state(c, m))
                .sorted(Comparator.comparing(MatchState::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Recently confirmed results, newest first, for corrections. */
    @Transactional(readOnly = true)
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('RESULT_CONFIRM', 'RESULT_CORRECT')")
    public List<MatchState> recentCompleted(int limit) {
        UUID t = tournament.currentTournamentId();
        List<Match> ms = matches.findByStatusIn(List.of(MatchStatus.COMPLETED)).stream()
                .filter(m -> fixtures.findById(m.getFixtureId()).map(f -> f.getTournamentId().equals(t)).orElse(false))
                .sorted(Comparator.comparing(Match::getUpdatedAt).reversed()).limit(limit).toList();
        Ctx c = ctx(ms);
        return ms.stream().map(m -> state(c, m)).toList();
    }

    // ---- Public --------------------------------------------------------------------------------

    /** Public card for one match (only for published fixtures). */
    @Transactional(readOnly = true)
    public PublicLiveMatch publicMatch(Match m) {
        Ctx c = ctx(List.of(m));
        return publicMatch(c, m);
    }

    private PublicLiveMatch publicMatch(Ctx c, Match m) {
        MatchState s = state(c, m);
        return new PublicLiveMatch(s.matchId(), s.fixtureNumber(), s.roundName(), s.groupName(), s.categoryCode(),
                s.categoryName(), s.teamA(), s.teamB(), s.sideA(), s.sideB(), s.courtName(), s.scheduledAt(), s.status(),
                s.status() == MatchStatus.SUBMITTED, s.games(), s.scoreA(), s.scoreB(), s.gamesA(), s.gamesB(), s.server(),
                s.winnerSide(), s.outcome(), s.stateVersion());
    }

    /** Live now, finished in the last few hours, and next up — published fixtures only. */
    @Transactional(readOnly = true)
    public PublicLive publicLive(Instant now) {
        UUID t = tournament.currentTournamentId();
        Map<UUID, TeamFixture> published = new HashMap<>();
        fixtures.findByTournamentIdOrderByFixtureNumberAsc(t).stream().filter(TeamFixture::isPublished)
                .forEach(f -> published.put(f.getId(), f));
        List<Match> ms = published.isEmpty() ? List.of()
                : matches.findByFixtureIdInOrderByMatchOrderAsc(published.keySet());
        Ctx c = ctx(ms);
        List<PublicLiveMatch> live = new ArrayList<>();
        List<PublicLiveMatch> recent = new ArrayList<>();
        List<PublicLiveMatch> next = new ArrayList<>();
        for (Match m : ms) {
            switch (m.getStatus()) {
                case LIVE -> live.add(publicMatch(c, m));
                case SUBMITTED, COMPLETED -> {
                    if (m.getUpdatedAt() != null && m.getUpdatedAt().isAfter(now.minus(RECENT))) {
                        recent.add(publicMatch(c, m));
                    }
                }
                case SCHEDULED -> {
                    if (m.getScheduledAt() != null && m.getScheduledAt().isAfter(now.minus(Duration.ofHours(1)))) {
                        next.add(publicMatch(c, m));
                    }
                }
                default -> {
                }
            }
        }
        Comparator<PublicLiveMatch> byTime = Comparator.comparing(PublicLiveMatch::scheduledAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
        live.sort(Comparator.comparing(PublicLiveMatch::courtName, Comparator.nullsLast(Comparator.naturalOrder())));
        recent.sort(byTime.reversed());
        next.sort(byTime);
        return new PublicLive(live, recent.stream().limit(12).toList(), next.stream().limit(6).toList());
    }
}
