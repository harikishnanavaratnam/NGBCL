package lk.ngbcl.standings.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.competition.service.FixtureService;
import lk.ngbcl.shared.site.SiteRevalidator;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.FieldErrors;
import lk.ngbcl.standings.domain.StandingsSettings;
import lk.ngbcl.standings.domain.StandingsSettingsRepository;
import lk.ngbcl.standings.service.StandingsDtos.GroupTable;
import lk.ngbcl.standings.service.StandingsDtos.RulesView;
import lk.ngbcl.standings.service.StandingsDtos.SeedingResult;
import lk.ngbcl.standings.service.StandingsDtos.SeedingSlot;
import lk.ngbcl.standings.service.StandingsDtos.SeedingView;
import lk.ngbcl.standings.service.StandingsDtos.StandingRow;
import lk.ngbcl.standings.service.StandingsDtos.StandingsView;
import lk.ngbcl.standings.service.StandingsDtos.TeamRef;
import lk.ngbcl.standings.service.StandingsEngine.Row;
import lk.ngbcl.standings.service.StandingsEngine.Rules;
import lk.ngbcl.standings.service.StandingsEngine.Tie;
import lk.ngbcl.standings.service.StandingsEngine.Tiebreaker;
import lk.ngbcl.team.service.TeamService;
import lk.ngbcl.team.service.TeamService.TeamCard;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Group and league tables (STA-001..006), computed on read from confirmed tie results, the
 * Settings › Standings rules, and filling knockout slots from final group positions (admin confirms).
 */
@Service
public class StandingsService {

    /** "Group A #1", "League #2", "Group B #1 (bye)". */
    static final Pattern SOURCE = Pattern.compile("^(.+) #(\\d+)(?: \\(bye\\))?$");
    static final String LEAGUE = "League";

    private final NamedParameterJdbcTemplate jdbc;
    private final StandingsSettingsRepository settings;
    private final TournamentService tournament;
    private final TeamService teams;
    private final FixtureService fixtures;
    private final AuditService audit;
    private final SiteRevalidator site;

    public StandingsService(NamedParameterJdbcTemplate jdbc, StandingsSettingsRepository settings,
            TournamentService tournament, TeamService teams, FixtureService fixtures, AuditService audit,
            SiteRevalidator site) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.tournament = tournament;
        this.teams = teams;
        this.fixtures = fixtures;
        this.audit = audit;
        this.site = site;
    }

    public record SettingsInput(int pointsWin, int pointsDraw, int pointsLoss, List<String> tiebreakers, Long version) {
    }

    // ---- Settings --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public StandingsSettings peek(UUID t) {
        return settings.findById(t).orElseGet(() -> new StandingsSettings(t));
    }

    @Transactional(readOnly = true)
    public RulesView rules() {
        return rulesView(peek(tournament.currentTournamentId()));
    }

    static RulesView rulesView(StandingsSettings s) {
        return new RulesView(s.getPointsWin(), s.getPointsDraw(), s.getPointsLoss(),
                StandingsEngine.parse(s.getTiebreakers()).stream().map(Enum::name).toList(), s.getVersion());
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('STANDINGS_MANAGE', 'SETTINGS_MANAGE')")
    public RulesView update(SettingsInput in) {
        UUID t = tournament.currentTournamentId();
        StandingsSettings s = settings.findById(t).orElseGet(() -> settings.saveAndFlush(new StandingsSettings(t)));
        if (in.version() != null && s.getVersion() != null && !Objects.equals(in.version(), s.getVersion())) {
            throw ApiException.conflict("STALE_DATA", "Someone else saved the standings rules. Reload to see their changes.");
        }
        FieldErrors errors = new FieldErrors();
        if (in.pointsWin() < 0 || in.pointsWin() > 10) {
            errors.add("pointsWin", "Between 0 and 10.");
        }
        if (in.pointsDraw() < 0 || in.pointsDraw() > 10) {
            errors.add("pointsDraw", "Between 0 and 10.");
        }
        if (in.pointsLoss() < 0 || in.pointsLoss() > 10) {
            errors.add("pointsLoss", "Between 0 and 10.");
        }
        if (!errors.has("pointsDraw") && (in.pointsDraw() > in.pointsWin() || in.pointsLoss() > in.pointsDraw())) {
            errors.add("pointsDraw", "A win must be worth at least a draw, and a draw at least a loss.");
        }
        List<String> tbs = in.tiebreakers() == null ? List.of() : in.tiebreakers();
        List<String> clean = new ArrayList<>();
        for (String s1 : tbs) {
            try {
                String n = Tiebreaker.valueOf(s1).name();
                if (!clean.contains(n)) {
                    clean.add(n);
                }
            } catch (IllegalArgumentException | NullPointerException e) {
                errors.add("tiebreakers", "Unknown tie-breaker: " + s1);
            }
        }
        errors.throwIfAny();
        Map<String, Object> before = snapshot(s);
        s.update(in.pointsWin(), in.pointsDraw(), in.pointsLoss(), String.join(",", clean));
        settings.flush();
        audit.record(AuditEvent.of("standings.settings.updated", "StandingsSettings", t).inTournament(t)
                .withChange(before, snapshot(s)));
        site.touch(SiteRevalidator.STANDINGS);
        return rulesView(s);
    }

    private static Map<String, Object> snapshot(StandingsSettings s) {
        Map<String, Object> m = new HashMap<>();
        m.put("pointsWin", s.getPointsWin());
        m.put("pointsDraw", s.getPointsDraw());
        m.put("pointsLoss", s.getPointsLoss());
        m.put("tiebreakers", s.getTiebreakers());
        return m;
    }

    // ---- Tables ----------------------------------------------------------------------------------

    /** Public tables: published ties only. */
    @Transactional(readOnly = true)
    public StandingsView publicStandings() {
        return standings(tournament.currentTournamentId(), true);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('STANDINGS_MANAGE', 'RESULT_CONFIRM', 'FIXTURE_MANAGE', 'REPORT_VIEW')")
    public StandingsView adminStandings() {
        return standings(tournament.currentTournamentId(), false);
    }

    /** One finished or unfinished tie row from the database. */
    public record TieRow(UUID id, UUID groupId, String stageType, UUID teamA, UUID teamB, String status, String result,
            int matchesA, int matchesB, int gamesA, int gamesB, int pointsA, int pointsB, int number, long order) {

        boolean done() {
            return "COMPLETED".equals(status);
        }

        Tie tie() {
            return new Tie(teamA, teamB, result, matchesA, matchesB, gamesA, gamesB, pointsA, pointsB, order);
        }
    }

    /** All ties of the edition (optionally published only) with game and point totals from confirmed matches. */
    public List<TieRow> ties(UUID t, boolean publishedOnly) {
        String sql = """
                SELECT f.id, f.group_id, s.type AS stage_type, f.team_a_id, f.team_b_id, f.status, f.result,
                       f.matches_won_a, f.matches_won_b, f.fixture_number,
                       COALESCE(SUM(m.games_a) FILTER (WHERE m.status = 'COMPLETED'), 0) AS ga,
                       COALESCE(SUM(m.games_b) FILTER (WHERE m.status = 'COMPLETED'), 0) AS gb,
                       COALESCE(SUM(m.points_a) FILTER (WHERE m.status = 'COMPLETED'), 0) AS pa,
                       COALESCE(SUM(m.points_b) FILTER (WHERE m.status = 'COMPLETED'), 0) AS pb
                FROM team_fixtures f
                JOIN stages s ON s.id = f.stage_id
                LEFT JOIN matches m ON m.fixture_id = f.id
                WHERE f.tournament_id = :t AND (:all OR f.published)
                GROUP BY f.id, s.type
                ORDER BY f.scheduled_at NULLS LAST, f.fixture_number
                """;
        List<TieRow> out = new ArrayList<>();
        jdbc.query(sql, new MapSqlParameterSource("t", t).addValue("all", !publishedOnly), rs -> {
            out.add(new TieRow(rs.getObject("id", UUID.class), rs.getObject("group_id", UUID.class),
                    rs.getString("stage_type"), rs.getObject("team_a_id", UUID.class), rs.getObject("team_b_id", UUID.class),
                    rs.getString("status"), rs.getString("result"), rs.getInt("matches_won_a"), rs.getInt("matches_won_b"),
                    rs.getInt("ga"), rs.getInt("gb"), rs.getInt("pa"), rs.getInt("pb"), rs.getInt("fixture_number"),
                    out.size()));
        });
        return out;
    }

    record GroupInfo(UUID id, String name, List<UUID> teamIds) {
    }

    private List<GroupInfo> groups(UUID t) {
        Map<UUID, GroupInfo> out = new LinkedHashMap<>();
        jdbc.query("SELECT id, name FROM competition_groups WHERE tournament_id = :t ORDER BY group_order",
                new MapSqlParameterSource("t", t),
                rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    out.put(id, new GroupInfo(id, rs.getString("name"), new ArrayList<>()));
                });
        jdbc.query("""
                SELECT gt.group_id, gt.team_id FROM group_teams gt
                JOIN competition_groups g ON g.id = gt.group_id
                WHERE g.tournament_id = :t ORDER BY gt.seed
                """, new MapSqlParameterSource("t", t), rs -> {
            GroupInfo g = out.get(rs.getObject("group_id", UUID.class));
            if (g != null) {
                g.teamIds().add(rs.getObject("team_id", UUID.class));
            }
        });
        return new ArrayList<>(out.values());
    }

    record Settings(String format, int qualifiersPerGroup) {
    }

    private Settings competitionSettings(UUID t) {
        List<Settings> s = jdbc.query(
                "SELECT format, qualifiers_per_group FROM competition_settings WHERE tournament_id = :t",
                new MapSqlParameterSource("t", t),
                (rs, i) -> new Settings(rs.getString("format"), rs.getInt("qualifiers_per_group")));
        return s.isEmpty() ? new Settings(null, 0) : s.get(0);
    }

    /** Highest "League #n" a knockout slot refers to (how many qualify from a league). */
    private int leagueQualifiers(UUID t) {
        int max = 0;
        for (KoRow k : knockout(t)) {
            for (String src : new String[] {k.sourceA(), k.sourceB()}) {
                Matcher m = src == null ? null : SOURCE.matcher(src);
                if (m != null && m.matches() && LEAGUE.equals(m.group(1))) {
                    max = Math.max(max, Integer.parseInt(m.group(2)));
                }
            }
        }
        return max;
    }

    record Computed(String name, boolean complete, int played, int total, int qualifiers, List<Row> rows) {
    }

    /** Group tables (or the single league table) keyed by name, in display order. */
    Map<String, Computed> compute(UUID t, boolean publishedOnly) {
        StandingsSettings st = peek(t);
        Rules rules = new Rules(st.getPointsWin(), st.getPointsDraw(), st.getPointsLoss(),
                StandingsEngine.parse(st.getTiebreakers()));
        Settings cs = competitionSettings(t);
        List<TieRow> all = ties(t, publishedOnly);
        Map<UUID, TeamCard> cards = teams.cards(t);
        Map<String, Computed> out = new LinkedHashMap<>();
        List<TieRow> league = all.stream().filter(x -> "LEAGUE".equals(x.stageType())).toList();
        if (!league.isEmpty()) {
            Map<UUID, String> members = new LinkedHashMap<>();
            for (TieRow x : league) {
                for (UUID id : new UUID[] {x.teamA(), x.teamB()}) {
                    if (id != null) {
                        members.putIfAbsent(id, name(cards, id));
                    }
                }
            }
            out.put(LEAGUE, table(LEAGUE, members, league, rules, leagueQualifiers(t)));
        }
        for (GroupInfo g : groups(t)) {
            List<TieRow> mine = all.stream().filter(x -> "GROUP".equals(x.stageType()) && g.id().equals(x.groupId())).toList();
            if (mine.isEmpty() && publishedOnly) {
                continue;
            }
            Map<UUID, String> members = new LinkedHashMap<>();
            g.teamIds().forEach(id -> members.put(id, name(cards, id)));
            out.put(g.name(), table(g.name(), members, mine, rules, cs.qualifiersPerGroup()));
        }
        return out;
    }

    private static String name(Map<UUID, TeamCard> cards, UUID id) {
        TeamCard c = cards.get(id);
        return c == null ? "" : c.name();
    }

    private static Computed table(String name, Map<UUID, String> members, List<TieRow> ties, Rules rules, int qualifiers) {
        List<Tie> done = ties.stream().filter(TieRow::done).map(TieRow::tie).toList();
        long countable = ties.stream().filter(x -> !"CANCELLED".equals(x.status())).count();
        boolean complete = countable > 0 && ties.stream().allMatch(x -> x.done() || "CANCELLED".equals(x.status()));
        return new Computed(name, complete, done.size(), (int) countable, qualifiers,
                StandingsEngine.table(members, done, rules));
    }

    private StandingsView standings(UUID t, boolean publishedOnly) {
        Map<UUID, TeamCard> cards = teams.cards(t);
        List<GroupTable> groups = compute(t, publishedOnly).values().stream()
                .map(c -> new GroupTable(c.name(), c.complete(), c.played(), c.total(), c.qualifiers(),
                        c.rows().stream().map(r -> row(cards, r, c.qualifiers())).toList()))
                .toList();
        return new StandingsView(competitionSettings(t).format(), rules(), groups);
    }

    private static StandingRow row(Map<UUID, TeamCard> cards, Row r, int qualifiers) {
        return new StandingRow(r.position, ref(cards.get(r.teamId)), r.played, r.won, r.drawn, r.lost, r.matchesWon,
                r.matchesLost, r.gamesWon, r.gamesLost, r.pointsWon, r.pointsLost, r.points, List.copyOf(r.form),
                r.tied && r.played > 0, r.position <= qualifiers);
    }

    static TeamRef ref(TeamCard c) {
        return c == null ? null : new TeamRef(c.slug(), c.name(), c.shortCode(), c.colorSlot(), c.logoUrl());
    }

    // ---- Knockout seeding ------------------------------------------------------------------------

    record KoRow(UUID id, int number, String roundName, UUID teamA, UUID teamB, String sourceA, String sourceB,
            String status) {

        boolean locked() {
            return "LIVE".equals(status) || "COMPLETED".equals(status);
        }
    }

    private List<KoRow> knockout(UUID t) {
        return jdbc.query("""
                SELECT f.id, f.fixture_number, f.round_name, f.team_a_id, f.team_b_id, f.source_a, f.source_b, f.status
                FROM team_fixtures f JOIN stages s ON s.id = f.stage_id
                WHERE f.tournament_id = :t AND s.type = 'KNOCKOUT'
                ORDER BY f.round_number, f.bracket_position NULLS LAST, f.fixture_number
                """, new MapSqlParameterSource("t", t),
                (rs, i) -> new KoRow(rs.getObject("id", UUID.class), rs.getInt("fixture_number"), rs.getString("round_name"),
                        rs.getObject("team_a_id", UUID.class), rs.getObject("team_b_id", UUID.class),
                        rs.getString("source_a"), rs.getString("source_b"), rs.getString("status")));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('STANDINGS_MANAGE', 'FIXTURE_MANAGE')")
    public SeedingView seeding() {
        return seeding(tournament.currentTournamentId());
    }

    private SeedingView seeding(UUID t) {
        Map<String, Computed> tables = compute(t, false);
        Map<UUID, TeamCard> cards = teams.cards(t);
        List<SeedingSlot> slots = new ArrayList<>();
        for (KoRow k : knockout(t)) {
            slot(k, "A", k.sourceA(), k.teamA(), tables, cards, slots);
            slot(k, "B", k.sourceB(), k.teamB(), tables, cards, slots);
        }
        boolean allComplete = !tables.isEmpty() && tables.values().stream().allMatch(Computed::complete);
        int pending = (int) slots.stream().filter(SeedingSlot::changes).count();
        return new SeedingView(allComplete, pending, slots);
    }

    private static void slot(KoRow k, String side, String source, UUID current, Map<String, Computed> tables,
            Map<UUID, TeamCard> cards, List<SeedingSlot> out) {
        Matcher m = source == null ? null : SOURCE.matcher(source);
        if (m == null || !m.matches()) {
            return;
        }
        Computed table = tables.get(m.group(1));
        int pos = Integer.parseInt(m.group(2));
        UUID proposed = null;
        boolean ready = false;
        String note = null;
        if (table == null) {
            note = "No table called “" + m.group(1) + "”.";
        } else if (!table.complete()) {
            note = m.group(1) + " isn’t finished yet (" + table.played() + " of " + table.total() + " ties confirmed).";
        } else if (pos > table.rows().size()) {
            note = m.group(1) + " has only " + table.rows().size() + " teams.";
        } else {
            Row r = table.rows().get(pos - 1);
            proposed = r.teamId;
            ready = true;
            if (r.tied) {
                note = "Level with another team on every tie-breaker — check before confirming.";
            }
        }
        boolean changes = ready && !k.locked() && !Objects.equals(proposed, current);
        if (k.locked() && ready && !Objects.equals(proposed, current)) {
            note = "This tie has started, so its teams can’t change.";
        }
        out.add(new SeedingSlot(k.id(), k.number(), k.roundName(), side, source, ref(cards.get(current)),
                ref(cards.get(proposed)), ready, k.locked(), changes, note));
    }

    /**
     * Puts the proposed teams into the knockout ties (through the fixture service, so the usual checks
     * and audit apply). Only finished groups are used; ties that have started are left alone. The
     * public site changes when the fixtures are published again.
     */
    @Transactional
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public SeedingResult applySeeding() {
        UUID t = tournament.currentTournamentId();
        SeedingView view = seeding(t);
        Map<UUID, KoRow> ko = new HashMap<>();
        knockout(t).forEach(k -> ko.put(k.id(), k));
        Map<UUID, UUID[]> next = new LinkedHashMap<>();
        for (SeedingSlot s : view.slots()) {
            if (!s.changes()) {
                continue;
            }
            KoRow k = ko.get(s.fixtureId());
            UUID[] pair = next.computeIfAbsent(k.id(), id -> new UUID[] {k.teamA(), k.teamB()});
            UUID proposed = proposedId(t, s);
            if ("A".equals(s.side())) {
                pair[0] = proposed;
            } else {
                pair[1] = proposed;
            }
        }
        if (next.isEmpty()) {
            throw ApiException.conflict("NOTHING_TO_APPLY", "There are no finished groups with places to fill.");
        }
        next.forEach((id, pair) -> fixtures.setTeams(id, pair[0], pair[1], null));
        audit.record(AuditEvent.of("standings.knockout_seeded", "Tournament", t).inTournament(t)
                .withChange(Map.of(), Map.of("ties", next.size())));
        return new SeedingResult(next.size(), seeding(t));
    }

    private UUID proposedId(UUID t, SeedingSlot s) {
        return teams.cards(t).values().stream().filter(c -> c.slug().equals(s.proposedTeam().slug()))
                .map(TeamCard::id).findFirst().orElseThrow();
    }
}
