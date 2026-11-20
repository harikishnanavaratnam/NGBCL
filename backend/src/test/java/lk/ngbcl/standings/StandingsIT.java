package lk.ngbcl.standings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import lk.ngbcl.TestcontainersConfiguration;
import lk.ngbcl.files.InMemoryObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * M7: group tables from confirmed ties (points and tie-breakers from settings), knockout seeding
 * confirmed by an admin, public standings and records, and the organiser reports. The group, its
 * ties and the knockout tie are inserted directly and removed afterwards (shared test database).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StandingsIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    InMemoryObjectStorage storage;

    @Autowired
    JdbcTemplate jdbc;

    String admin;

    JsonNode call(MockHttpServletRequestBuilder b, String bearer, String regToken, Object body, int expected)
            throws Exception {
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        if (regToken != null) {
            b.header("X-Registration-Token", regToken);
        }
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        var r = mvc.perform(b).andReturn();
        String s = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).as(s).isEqualTo(expected);
        return s.isEmpty() ? null : json.readTree(s);
    }

    JsonNode call(MockHttpServletRequestBuilder b, Object body, int expected) throws Exception {
        return call(b, admin, null, body, expected);
    }

    @Test
    void tablesSeedingStatsAndReports() throws Exception {
        var login = mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Test-Admin-Password-1\"}")).andReturn();
        admin = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        openWindows();
        String n = Long.toString(ThreadLocalRandom.current().nextLong(10_000, 99_999));

        // ---- Settings: defaults, validation ----
        JsonNode rules = call(get("/api/admin/standings/settings"), null, 200);
        assertThat(rules.get("pointsWin").asInt()).isEqualTo(2);
        assertThat(rules.get("tiebreakers").toString()).contains("MATCHES_WON", "HEAD_TO_HEAD");
        call(put("/api/admin/standings/settings"), Map.of("pointsWin", 1, "pointsDraw", 2, "pointsLoss", 0,
                "tiebreakers", List.of("MATCHES_WON")), 400);
        call(put("/api/admin/standings/settings"), Map.of("pointsWin", 2, "pointsDraw", 1, "pointsLoss", 0,
                "tiebreakers", List.of("NOPE")), 400);

        // ---- A three-team group, all ties confirmed: X beats Y and Z, Y beats Z ----
        JsonNode setup = call(get("/api/admin/competition/setup"), null, 200);
        String ms = null;
        for (JsonNode c : setup.get("categories")) {
            if ("MS".equals(c.get("code").asText())) {
                ms = c.get("id").asText();
            }
        }
        UUID x = UUID.fromString(team("Xeno " + n));
        UUID y = UUID.fromString(team("Yarl " + n));
        UUID z = UUID.fromString(team("Zest " + n));
        String px = squadPlayer(x.toString(), "Xavier Stand" + n, "MALE", n + "1");
        String py = squadPlayer(y.toString(), "Yogan Stand" + n, "MALE", n + "2");
        String pz = squadPlayer(z.toString(), "Zahir Stand" + n, "MALE", n + "3");
        UUID t = jdbc.queryForObject("SELECT id FROM tournaments WHERE is_current", UUID.class);
        int order = 2000 + ThreadLocalRandom.current().nextInt(100_000);
        UUID gstage = UUID.randomUUID();
        UUID kstage = UUID.randomUUID();
        jdbc.update("INSERT INTO stages (id, tournament_id, type, name, stage_order, created_at) VALUES (?, ?, 'GROUP', 'Groups test', ?, now())",
                gstage, t, order);
        jdbc.update("INSERT INTO stages (id, tournament_id, type, name, stage_order, created_at) VALUES (?, ?, 'KNOCKOUT', 'KO test', ?, now())",
                kstage, t, order + 1);
        String gname = "G" + n;
        UUID group = UUID.randomUUID();
        jdbc.update("INSERT INTO competition_groups (id, tournament_id, name, group_order, created_at) VALUES (?, ?, ?, ?, now())",
                group, t, gname, order);
        int seed = 1;
        for (UUID team : List.of(x, y, z)) {
            jdbc.update("INSERT INTO group_teams (id, group_id, team_id, seed) VALUES (?, ?, ?, ?)", UUID.randomUUID(), group, team, seed++);
        }
        int num = 100_000 + ThreadLocalRandom.current().nextInt(800_000);
        UUID t1 = tie(t, gstage, group, num, x, y, "A", ms, px, py);
        UUID t2 = tie(t, gstage, group, num + 1, y, z, "A", ms, py, pz);
        UUID t3 = tie(t, gstage, group, num + 2, z, x, "B", ms, pz, px);
        UUID ko = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO team_fixtures (id, tournament_id, stage_id, fixture_number, round_number, round_name, source_a,
                  source_b, status, published, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, 'Final', ?, ?, 'SCHEDULED', true, now(), now())""",
                ko, t, kstage, num + 3, gname + " #1", gname + " #2");

        JsonNode table = group(call(get("/api/admin/standings"), null, 200), gname);
        assertThat(table.get("complete").asBoolean()).isTrue();
        assertThat(table.at("/rows/0/team/name").asText()).isEqualTo("Xeno " + n);
        assertThat(table.at("/rows/0/points").asInt()).isEqualTo(4);
        assertThat(table.at("/rows/0/won").asInt()).isEqualTo(2);
        assertThat(table.at("/rows/0/gamesWon").asInt()).isEqualTo(4);
        assertThat(table.at("/rows/1/team/name").asText()).isEqualTo("Yarl " + n);
        assertThat(table.at("/rows/2/points").asInt()).isEqualTo(0);
        assertThat(table.at("/rows/0/form").toString()).isEqualTo("[\"W\",\"W\"]");

        // Rules come from settings: 3 points for a win
        call(put("/api/admin/standings/settings"), Map.of("pointsWin", 3, "pointsDraw", 1, "pointsLoss", 0,
                "tiebreakers", List.of("MATCHES_WON", "GAME_DIFF", "POINT_DIFF", "HEAD_TO_HEAD")), 200);
        assertThat(group(call(get("/api/admin/standings"), null, 200), gname).at("/rows/0/points").asInt()).isEqualTo(6);

        // Public: published ties only
        JsonNode pub = group(call(get("/api/public/standings"), null, null, null, 200), gname);
        assertThat(pub.get("rows").size()).isEqualTo(3);

        // ---- Knockout seeding: proposed from the final table, applied by an admin ----
        JsonNode seeding = call(get("/api/admin/standings/seeding"), null, 200);
        JsonNode slot = null;
        for (JsonNode s : seeding.get("slots")) {
            if (s.get("fixtureId").asText().equals(ko.toString()) && "A".equals(s.get("side").asText())) {
                slot = s;
            }
        }
        assertThat(slot).isNotNull();
        assertThat(slot.get("ready").asBoolean()).isTrue();
        assertThat(slot.at("/proposedTeam/name").asText()).isEqualTo("Xeno " + n);
        assertThat(slot.get("changes").asBoolean()).isTrue();
        JsonNode applied = call(post("/api/admin/standings/seeding/apply"), null, 200);
        assertThat(applied.get("tiesUpdated").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT team_a_id FROM team_fixtures WHERE id = ?", UUID.class, ko)).isEqualTo(x);
        assertThat(jdbc.queryForObject("SELECT team_b_id FROM team_fixtures WHERE id = ?", UUID.class, ko)).isEqualTo(y);
        assertThat(jdbc.queryForObject("SELECT changed_since_publish FROM team_fixtures WHERE id = ?", Boolean.class, ko)).isTrue();

        // ---- Public records ----
        JsonNode stats = call(get("/api/public/stats"), null, null, null, 200);
        JsonNode xs = null;
        for (JsonNode p : stats.get("players")) {
            if (p.get("fullName").asText().equals("Xavier Stand" + n)) {
                xs = p;
            }
        }
        assertThat(xs).isNotNull();
        assertThat(xs.get("played").asInt()).isEqualTo(2);
        assertThat(xs.get("won").asInt()).isEqualTo(2);
        assertThat(xs.at("/categories/0/code").asText()).isEqualTo("MS");
        assertThat(stats.get("teams").toString()).contains("Xeno " + n);
        assertThat(stats.get("leaders").toString()).contains("Xavier Stand" + n);
        assertThat(stats.toString()).doesNotContain("@example.com");

        // ---- Reports ----
        JsonNode results = call(get("/api/admin/reports/results"), null, 200);
        assertThat(results.at("/sheets/0/name").asText()).isEqualTo("Matches");
        assertThat(results.toString()).contains("Xeno " + n, "21-15");
        JsonNode standingsReport = call(get("/api/admin/reports/standings"), null, 200);
        assertThat(standingsReport.toString()).contains(gname, "Yarl " + n);
        JsonNode players = call(get("/api/admin/reports/players"), null, 200);
        assertThat(players.toString()).contains("Zahir Stand" + n, "Zest " + n);
        JsonNode auction = call(get("/api/admin/reports/auction"), null, 200);
        assertThat(auction.at("/sheets/0/name").asText()).isEqualTo("Summary");
        assertThat(auction.toString()).contains("\"name\":\"Xeno " + n + "\"", "All players", "Bid log");
        String today = LocalDate.now(ZoneId.of("Asia/Colombo")).toString();
        JsonNode audit = call(get("/api/admin/reports/audit?from=" + today + "&to=" + today), null, 200);
        assertThat(audit.toString()).contains("standings.settings.updated", "standings.knockout_seeded");
        call(get("/api/admin/reports/audit?from=yesterday"), null, 400);
        call(get("/api/admin/reports/results"), null, null, null, 401);

        // ---- Put things back for the other stories ----
        call(put("/api/admin/standings/settings"), Map.of("pointsWin", 2, "pointsDraw", 1, "pointsLoss", 0,
                "tiebreakers", List.of("MATCHES_WON", "GAME_DIFF", "POINT_DIFF", "HEAD_TO_HEAD")), 200);
        for (UUID f : List.of(t1, t2, t3, ko)) {
            jdbc.update("DELETE FROM team_fixtures WHERE id = ?", f);
        }
        jdbc.update("DELETE FROM group_teams WHERE group_id = ?", group);
        jdbc.update("DELETE FROM competition_groups WHERE id = ?", group);
        jdbc.update("DELETE FROM stages WHERE id IN (?, ?)", gstage, kstage);
    }

    static JsonNode group(JsonNode view, String name) {
        for (JsonNode g : view.get("groups")) {
            if (g.get("name").asText().equals(name)) {
                return g;
            }
        }
        throw new AssertionError("No table " + name + " in " + view);
    }

    /** A confirmed one-match tie; the winner takes the match 21-15 21-18. */
    UUID tie(UUID t, UUID stage, UUID group, int number, UUID a, UUID b, String winner, String ms, String pa, String pb) {
        UUID id = UUID.randomUUID();
        boolean aw = "A".equals(winner);
        jdbc.update("""
                INSERT INTO team_fixtures (id, tournament_id, stage_id, group_id, fixture_number, round_number, round_name,
                  team_a_id, team_b_id, status, published, scheduled_at, matches_won_a, matches_won_b, result,
                  winner_team_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 1, 'Round 1', ?, ?, 'COMPLETED', true, now(), ?, ?, ?, ?, now(), now())""",
                id, t, stage, group, number, a, b, aw ? 1 : 0, aw ? 0 : 1, winner, aw ? a : b);
        UUID m = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO matches (id, fixture_id, match_order, category_id, players_per_side, status, outcome, winner_side,
                  games_a, games_b, points_a, points_b, confirmed_at, created_at, updated_at)
                VALUES (?, ?, 1, ?, 1, 'COMPLETED', 'NORMAL', ?, ?, ?, ?, ?, now(), now(), now())""",
                m, id, UUID.fromString(ms), winner, aw ? 2 : 0, aw ? 0 : 2, aw ? 42 : 33, aw ? 33 : 42);
        jdbc.update("INSERT INTO match_games (id, match_id, game_number, score_a, score_b, winner_side) VALUES (?, ?, 1, ?, ?, ?)",
                UUID.randomUUID(), m, aw ? 21 : 15, aw ? 15 : 21, winner);
        jdbc.update("INSERT INTO match_games (id, match_id, game_number, score_a, score_b, winner_side) VALUES (?, ?, 2, ?, ?, ?)",
                UUID.randomUUID(), m, aw ? 21 : 18, aw ? 18 : 21, winner);
        jdbc.update("INSERT INTO match_players (id, match_id, side, position, team_id, tournament_player_id) VALUES (?, ?, 'A', 1, ?, ?)",
                UUID.randomUUID(), m, a, UUID.fromString(pa));
        jdbc.update("INSERT INTO match_players (id, match_id, side, position, team_id, tournament_player_id) VALUES (?, ?, 'B', 1, ?, ?)",
                UUID.randomUUID(), m, b, UUID.fromString(pb));
        return id;
    }

    String team(String name) throws Exception {
        return call(post("/api/admin/teams"), Map.of("name", name), 201).get("id").asText();
    }

    /** Registers and approves a player, then puts them in the team's squad directly. */
    String squadPlayer(String teamId, String name, String gender, String digits) throws Exception {
        String email = "stand" + digits + "@example.com";
        String token = call(post("/api/public/registration/sessions"), null, null, Map.of("kind", "PLAYER"), 201)
                .get("token").asText();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 750, BufferedImage.TYPE_INT_RGB), "png", out);
        byte[] photo = out.toByteArray();
        JsonNode ticket = call(post("/api/public/registration/uploads"), null, token,
                Map.of("contentType", "image/png", "size", photo.length), 201);
        storage.simulateUpload(ticket.get("uploadUrl").asText(), photo);
        String photoId = ticket.get("assetId").asText();
        call(post("/api/public/registration/uploads/" + photoId + "/complete"), null, token, null, 200);
        Map<String, Object> p = new HashMap<>();
        p.put("fullName", name);
        p.put("email", email);
        p.put("phone", "+9475" + String.format("%07d", Long.parseLong(digits) % 10_000_000));
        p.put("dateOfBirth", "1999-04-04");
        p.put("gender", gender);
        p.put("homeArea", "Kopay");
        p.put("emergencyName", "Contact " + digits);
        p.put("emergencyPhone", "+9472" + String.format("%07d", Long.parseLong(digits) % 10_000_000));
        p.put("photoAssetId", photoId);
        p.put("jaffnaDeclared", true);
        p.put("consent", true);
        call(post("/api/public/registration/player"), null, token, p, 201);
        String regId = call(get("/api/admin/registrations/players?status=PENDING&q=" + email), null, 200)
                .at("/content/0/id").asText();
        call(post("/api/admin/registrations/players/" + regId + "/photo/verify"), Map.of("approve", true), 200);
        call(post("/api/admin/registrations/players/" + regId + "/approve"), Map.of(), 200);
        String entry = jdbc.queryForObject("""
                SELECT tp.id::text FROM tournament_players tp JOIN players p ON p.id = tp.player_id
                WHERE p.full_name = ?""", String.class, name);
        jdbc.update("""
                INSERT INTO team_players (id, tournament_id, team_id, tournament_player_id, source, created_at, updated_at)
                SELECT ?, tp.tournament_id, ?, tp.id, 'ADMIN', now(), now() FROM tournament_players tp WHERE tp.id = ?
                """, UUID.randomUUID(), UUID.fromString(teamId), UUID.fromString(entry));
        return entry;
    }

    void openWindows() throws Exception {
        JsonNode s = call(get("/api/admin/settings"), null, 200);
        if ("DRAFT".equals(s.at("/general/status").asText())) {
            call(put("/api/admin/tournament/status"), Map.of("status", "UPCOMING"), 200);
            s = call(get("/api/admin/settings"), null, 200);
        }
        ObjectNode reg = (ObjectNode) s.get("registration").deepCopy();
        Instant now = Instant.now();
        reg.put("playerOpensAt", now.minus(1, ChronoUnit.DAYS).toString());
        reg.put("playerClosesAt", now.plus(10, ChronoUnit.DAYS).toString());
        reg.put("teamOpensAt", now.minus(1, ChronoUnit.DAYS).toString());
        reg.put("teamClosesAt", now.plus(10, ChronoUnit.DAYS).toString());
        reg.put("categoryRequired", false);
        reg.put("settingsVersion", s.get("settingsVersion").asLong());
        call(put("/api/admin/settings/registration"), reg, 200);
    }
}
