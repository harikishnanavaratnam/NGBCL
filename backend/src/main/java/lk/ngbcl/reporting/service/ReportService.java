package lk.ngbcl.reporting.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.ngbcl.standings.service.StandingsDtos.GroupTable;
import lk.ngbcl.standings.service.StandingsDtos.StandingRow;
import lk.ngbcl.standings.service.StandingsService;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organiser exports (RPT-001..006). Each report is a set of sheets (columns + rows) that the admin
 * app saves as Excel or CSV; the server decides who may see what. Times are in Asia/Colombo.
 */
@Service
public class ReportService {

    static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZONE);
    static final int AUDIT_LIMIT = 20_000;

    private final NamedParameterJdbcTemplate jdbc;
    private final TournamentService tournament;
    private final StandingsService standings;
    private final Clock clock;

    public ReportService(NamedParameterJdbcTemplate jdbc, TournamentService tournament, StandingsService standings,
            Clock clock) {
        this.jdbc = jdbc;
        this.tournament = tournament;
        this.standings = standings;
        this.clock = clock;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Sheet(String name, List<String> columns, List<List<Object>> rows) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Report(String title, String fileName, String generatedAt, List<Sheet> sheets) {
    }

    private Report report(String title, String file, List<Sheet> sheets) {
        String day = LocalDate.now(clock.withZone(ZONE)).toString();
        return new Report(title, "ngbcl-" + file + "-" + day, TIME.format(clock.instant()), sheets);
    }

    private Sheet sheet(String name, List<String> columns, String sql, MapSqlParameterSource p) {
        return new Sheet(name, columns, jdbc.query(sql, p, (rs, i) -> row(rs)));
    }

    private static List<Object> row(ResultSet rs) throws SQLException {
        int n = rs.getMetaData().getColumnCount();
        List<Object> out = new ArrayList<>(n);
        for (int c = 1; c <= n; c++) {
            Object v = rs.getObject(c);
            if (v instanceof Timestamp ts) {
                v = TIME.format(ts.toInstant());
            } else if (v instanceof java.time.OffsetDateTime odt) {
                v = TIME.format(odt.toInstant());
            } else if (v instanceof UUID u) {
                v = u.toString();
            } else if (v instanceof Boolean b) {
                v = b ? "Yes" : "No";
            }
            out.add(v);
        }
        return out;
    }

    private MapSqlParameterSource t() {
        return new MapSqlParameterSource("t", tournament.currentTournamentId());
    }

    // ---- Results ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public Report results() {
        String sql = """
                SELECT f.fixture_number, s.name AS stage, COALESCE(g.name, f.round_name) AS grp, f.round_name,
                       m.match_order, COALESCE(m.scheduled_at, f.scheduled_at) AS at, ct.name AS court, c.code,
                       ta.name AS team_a,
                       (SELECT string_agg(p.full_name, ' / ' ORDER BY mp.position) FROM match_players mp
                          JOIN tournament_players tp ON tp.id = mp.tournament_player_id JOIN players p ON p.id = tp.player_id
                         WHERE mp.match_id = m.id AND mp.side = 'A') AS players_a,
                       tb.name AS team_b,
                       (SELECT string_agg(p.full_name, ' / ' ORDER BY mp.position) FROM match_players mp
                          JOIN tournament_players tp ON tp.id = mp.tournament_player_id JOIN players p ON p.id = tp.player_id
                         WHERE mp.match_id = m.id AND mp.side = 'B') AS players_b,
                       m.status,
                       (SELECT string_agg(mg.score_a || '-' || mg.score_b, '  ' ORDER BY mg.game_number) FROM match_games mg
                         WHERE mg.match_id = m.id AND mg.score_a + mg.score_b > 0) AS games,
                       m.games_a, m.games_b,
                       CASE m.winner_side WHEN 'A' THEN ta.name WHEN 'B' THEN tb.name END AS winner,
                       m.outcome, m.outcome_note, su.display_name AS scorer, sb.display_name AS submitted_by,
                       m.submitted_at, cb.display_name AS confirmed_by, m.confirmed_at
                FROM matches m
                JOIN team_fixtures f ON f.id = m.fixture_id
                JOIN stages s ON s.id = f.stage_id
                LEFT JOIN competition_groups g ON g.id = f.group_id
                LEFT JOIN courts ct ON ct.id = COALESCE(m.court_id, f.court_id)
                JOIN match_categories c ON c.id = m.category_id
                LEFT JOIN teams ta ON ta.id = f.team_a_id
                LEFT JOIN teams tb ON tb.id = f.team_b_id
                LEFT JOIN users su ON su.id = m.scorer_user_id
                LEFT JOIN users sb ON sb.id = m.submitted_by
                LEFT JOIN users cb ON cb.id = m.confirmed_by
                WHERE f.tournament_id = :t
                ORDER BY s.stage_order, f.fixture_number, m.match_order
                """;
        List<String> cols = List.of("Tie", "Stage", "Group / round", "Round", "Match", "Time", "Court", "Event",
                "Team A", "Players A", "Team B", "Players B", "Status", "Game scores", "Games A", "Games B", "Winner",
                "Outcome", "Note", "Scorer", "Submitted by", "Submitted at", "Confirmed by", "Confirmed at");
        Sheet matches = sheet("Matches", cols, sql, t());

        String ties = """
                SELECT f.fixture_number, s.name AS stage, COALESCE(g.name, '') AS grp, f.round_name, f.scheduled_at,
                       ct.name AS court, COALESCE(ta.name, f.source_a) AS team_a, COALESCE(tb.name, f.source_b) AS team_b,
                       f.status, f.matches_won_a, f.matches_won_b,
                       CASE f.result WHEN 'A' THEN ta.name WHEN 'B' THEN tb.name WHEN 'DRAW' THEN 'Draw' END AS result,
                       f.published
                FROM team_fixtures f
                JOIN stages s ON s.id = f.stage_id
                LEFT JOIN competition_groups g ON g.id = f.group_id
                LEFT JOIN courts ct ON ct.id = f.court_id
                LEFT JOIN teams ta ON ta.id = f.team_a_id
                LEFT JOIN teams tb ON tb.id = f.team_b_id
                WHERE f.tournament_id = :t
                ORDER BY s.stage_order, f.fixture_number
                """;
        Sheet tieSheet = sheet("Ties", List.of("Tie", "Stage", "Group", "Round", "Time", "Court", "Team A", "Team B",
                "Status", "Matches A", "Matches B", "Result", "Published"), ties, t());
        return report("Results", "results", List.of(matches, tieSheet));
    }

    // ---- Standings -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public Report standingsReport() {
        List<List<Object>> rows = new ArrayList<>();
        for (GroupTable g : standings.adminStandings().groups()) {
            for (StandingRow r : g.rows()) {
                List<Object> row = new ArrayList<>();
                row.add(g.name());
                row.add(r.position());
                row.add(r.team() == null ? "" : r.team().name());
                row.add(r.played());
                row.add(r.won());
                row.add(r.drawn());
                row.add(r.lost());
                row.add(r.matchesWon());
                row.add(r.matchesLost());
                row.add(r.gamesWon());
                row.add(r.gamesLost());
                row.add(r.pointsWon());
                row.add(r.pointsLost());
                row.add(r.points());
                row.add(String.join("", r.form()));
                row.add(r.qualifies() ? "Yes" : "");
                row.add(g.complete() ? "Final" : "In progress");
                rows.add(row);
            }
        }
        Sheet s = new Sheet("Standings", List.of("Group", "Pos", "Team", "Played", "Won", "Drawn", "Lost", "Matches won",
                "Matches lost", "Games won", "Games lost", "Points won", "Points lost", "Table points", "Form", "Qualifies",
                "Group status"), rows);
        return report("Standings", "standings", List.of(s));
    }

    // ---- Players master --------------------------------------------------------------------------

    static final String PLAYERS_SQL = """
            SELECT r.registration_number, r.full_name, r.gender, pc.name AS category, r.home_area, r.phone, r.email,
                   r.status AS registration_status, r.submitted_at, tp.status AS edition_status,
                   l.state AS auction_state, l.round, l.lot_number, l.base_price, l.bid_count,
                   tm.name AS team, tpl.source, tpl.purchase_price, l.closed_at
            FROM player_registrations r
            LEFT JOIN player_categories pc ON pc.id = r.skill_category_id
            LEFT JOIN tournament_players tp ON tp.registration_id = r.id
            LEFT JOIN auction_lots l ON l.tournament_player_id = tp.id
            LEFT JOIN team_players tpl ON tpl.tournament_player_id = tp.id AND tpl.active
            LEFT JOIN teams tm ON tm.id = tpl.team_id
            WHERE r.tournament_id = :t
            ORDER BY r.registration_number
            """;
    static final List<String> PLAYER_COLS = List.of("Reg no", "Full name", "Gender", "Skill category", "Home area",
            "Phone", "Email", "Registration", "Registered at", "Edition status", "Auction state", "Round", "Lot no",
            "Base price (LKR)", "Bids", "Team", "Added by", "Price paid (LKR)", "Sold at");

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public Report players() {
        return report("Players master list", "players", List.of(sheet("Players", PLAYER_COLS, PLAYERS_SQL, t())));
    }

    // ---- Auction workbook ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public Report auction() {
        MapSqlParameterSource p = t();
        List<Sheet> sheets = new ArrayList<>();
        sheets.add(sheet("Summary", List.of("Team", "Code", "Budget (LKR)", "Adjustments (LKR)", "Spent (LKR)",
                "Remaining (LKR)", "Squad size"), """
                SELECT tm.name, tm.short_code, b.initial_budget, b.adjustments, b.spent, b.remaining, b.squad_count
                FROM team_budgets b JOIN auctions a ON a.id = b.auction_id JOIN teams tm ON tm.id = b.team_id
                WHERE a.tournament_id = :t
                ORDER BY lower(tm.name)
                """, p));

        // One sheet per team, in name order (teams without players still get an empty sheet).
        Map<String, List<List<Object>>> byTeam = new LinkedHashMap<>();
        jdbc.query("SELECT name FROM teams WHERE tournament_id = :t AND status = 'ACTIVE' ORDER BY lower(name)", p,
                rs -> {
                    byTeam.put(rs.getString("name"), new ArrayList<>());
                });
        jdbc.query("""
                SELECT tm.name AS team, tpl.lot_number, p.full_name, r.registration_number, pc.name AS category, r.gender,
                       r.phone, tpl.source, l.base_price, tpl.purchase_price, l.round, tr.confirmed_at
                FROM team_players tpl
                JOIN teams tm ON tm.id = tpl.team_id
                JOIN tournament_players tp ON tp.id = tpl.tournament_player_id
                JOIN players p ON p.id = tp.player_id
                JOIN player_registrations r ON r.id = tp.registration_id
                LEFT JOIN player_categories pc ON pc.id = tp.skill_category_id
                LEFT JOIN auction_lots l ON l.tournament_player_id = tp.id
                LEFT JOIN auction_transactions tr ON tr.id = tpl.transaction_id
                WHERE tpl.tournament_id = :t AND tpl.active
                ORDER BY lower(tm.name), tpl.lot_number NULLS LAST, p.full_name
                """, p, rs -> {
            List<Object> r = row(rs);
            byTeam.computeIfAbsent((String) r.get(0), k -> new ArrayList<>()).add(r.subList(1, r.size()));
        });
        List<String> teamCols = List.of("Lot no", "Player", "Reg no", "Skill category", "Gender", "Phone", "Added by",
                "Base price (LKR)", "Price paid (LKR)", "Round", "Sold at");
        byTeam.forEach((team, rows) -> sheets.add(new Sheet(team, teamCols, rows)));

        sheets.add(sheet("All players", PLAYER_COLS, PLAYERS_SQL, p));
        sheets.add(sheet("Sales log", List.of("Lot no", "Player", "Team", "Amount (LKR)", "Status", "Confirmed at",
                "Confirmed by", "Reversed at", "Reversed by"), """
                SELECT l.lot_number, p.full_name, tm.name, tr.amount, tr.status, tr.confirmed_at, cu.display_name,
                       tr.reversed_at, ru.display_name
                FROM auction_transactions tr
                JOIN auctions a ON a.id = tr.auction_id
                JOIN auction_lots l ON l.id = tr.lot_id
                JOIN tournament_players tp ON tp.id = tr.tournament_player_id
                JOIN players p ON p.id = tp.player_id
                JOIN teams tm ON tm.id = tr.team_id
                LEFT JOIN users cu ON cu.id = tr.confirmed_by
                LEFT JOIN users ru ON ru.id = tr.reversed_by
                WHERE a.tournament_id = :t
                ORDER BY tr.confirmed_at
                """, p));
        sheets.add(sheet("Bid log", List.of("Lot no", "Player", "Round", "Bid no", "Team", "Amount (LKR)", "Status",
                "Placed at", "Placed by", "Voided at"), """
                SELECT l.lot_number, p.full_name, b.round, b.bid_number, tm.name, b.amount, b.status, b.created_at,
                       u.display_name, b.voided_at
                FROM auction_bids b
                JOIN auction_lots l ON l.id = b.lot_id
                JOIN auctions a ON a.id = l.auction_id
                JOIN tournament_players tp ON tp.id = l.tournament_player_id
                JOIN players p ON p.id = tp.player_id
                JOIN teams tm ON tm.id = b.team_id
                LEFT JOIN users u ON u.id = b.placed_by
                WHERE a.tournament_id = :t
                ORDER BY b.created_at
                """, p));
        sheets.add(sheet("Budget adjustments", List.of("Time", "Team", "Amount (LKR)", "Reason", "By"), """
                SELECT ba.created_at, tm.name, ba.amount, ba.reason, u.display_name
                FROM budget_adjustments ba
                JOIN team_budgets b ON b.id = ba.budget_id
                JOIN auctions a ON a.id = b.auction_id
                JOIN teams tm ON tm.id = b.team_id
                LEFT JOIN users u ON u.id = ba.actor_id
                WHERE a.tournament_id = :t
                ORDER BY ba.created_at
                """, p));
        return report("Auction workbook", "auction", sheets);
    }

    // ---- Audit log -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public Report audit(Instant from, Instant to) {
        MapSqlParameterSource p = t();
        StringBuilder sql = new StringBuilder("""
                SELECT occurred_at, actor_label, action, entity_type, entity_id, reason,
                       CAST(before_state AS text) AS before_state, CAST(after_state AS text) AS after_state
                FROM audit_logs WHERE tournament_id = :t
                """);
        if (from != null) {
            sql.append(" AND occurred_at >= :from");
            p.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND occurred_at < :to");
            p.addValue("to", Timestamp.from(to));
        }
        sql.append(" ORDER BY occurred_at DESC LIMIT ").append(AUDIT_LIMIT);
        return report("Audit log", "audit", List.of(sheet("Audit log", List.of("Time", "Who", "Action", "Record type",
                "Record id", "Reason", "Before", "After"), sql.toString(), p)));
    }
}
