package lk.ngbcl.scoring;

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
 * M6: rally scoring with deuce, idempotent taps, undo across games, two-step approval with return,
 * per-match rules, manual entry, tie result, knockout progression and audited corrections.
 * The tie is inserted directly so this story doesn't depend on the edition's generated fixtures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ScoringIT {

    static final String S = "/api/scorer/matches/";

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

    JsonNode tap(String match, String side, String key) throws Exception {
        return call(post(S + match + "/point"), Map.of("side", side, "clientEventId", key), 200);
    }

    JsonNode taps(String match, String side, int n) throws Exception {
        JsonNode last = null;
        for (int i = 0; i < n; i++) {
            last = tap(match, side, UUID.randomUUID().toString());
        }
        return last;
    }

    static Map<String, Object> ev() {
        return Map.of("clientEventId", UUID.randomUUID().toString());
    }

    @Test
    void scoreConfirmAndProgress() throws Exception {
        var login = mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Test-Admin-Password-1\"}")).andReturn();
        JsonNode auth = json.readTree(login.getResponse().getContentAsString());
        admin = auth.get("accessToken").asText();
        String adminId = auth.at("/user/id").asText();
        openWindows();
        String n = Long.toString(ThreadLocalRandom.current().nextLong(10_000, 99_999));

        // ---- Rules ----
        JsonNode rules = call(get("/api/admin/scoring/settings"), null, 200);
        assertThat(rules.get("pointsToWin").asInt()).isEqualTo(21);
        assertThat(rules.get("pointCap").asInt()).isEqualTo(30);
        assertThat(rules.get("twoStepApproval").asBoolean()).isTrue();
        Map<String, Object> bad = new HashMap<>(Map.of("pointsToWin", 21, "winBy", 2, "pointCap", 15, "gamesToWin", 2,
                "allowWalkover", true, "allowRetirement", true, "twoStepApproval", true));
        call(put("/api/admin/scoring/settings"), bad, 400);

        // ---- A published tie with two singles, feeding a final ----
        JsonNode setup = call(get("/api/admin/competition/setup"), null, 200);
        String ms = null;
        for (JsonNode c : setup.get("categories")) {
            if ("MS".equals(c.get("code").asText())) {
                ms = c.get("id").asText();
            }
        }
        String ta = team("Score A " + n);
        String tb = team("Score B " + n);
        String a1 = squadPlayer(ta, "Ajith S " + n, "MALE", n + "1");
        String a2 = squadPlayer(ta, "Bavan S " + n, "MALE", n + "2");
        String b1 = squadPlayer(tb, "Cyril S " + n, "MALE", n + "3");
        String b2 = squadPlayer(tb, "Dilan S " + n, "MALE", n + "4");
        UUID t = jdbc.queryForObject("SELECT id FROM tournaments WHERE is_current", UUID.class);
        UUID stage = UUID.randomUUID();
        jdbc.update("INSERT INTO stages (id, tournament_id, type, name, stage_order, created_at) VALUES (?, ?, 'KNOCKOUT', 'Test', ?, now())",
                stage, t, 1000 + ThreadLocalRandom.current().nextInt(100_000));
        int num = 100_000 + ThreadLocalRandom.current().nextInt(800_000);
        UUID fin = UUID.randomUUID();
        UUID tie = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO team_fixtures (id, tournament_id, stage_id, fixture_number, round_number, round_name, status,
                  published, created_at, updated_at) VALUES (?, ?, ?, ?, 2, 'Final', 'SCHEDULED', true, now(), now())""",
                fin, t, stage, num + 1);
        jdbc.update("""
                INSERT INTO team_fixtures (id, tournament_id, stage_id, fixture_number, round_number, round_name, team_a_id,
                  team_b_id, winner_to_fixture_id, winner_to_side, status, published, scheduled_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, 'Semi-finals', ?, ?, ?, 'A', 'SCHEDULED', true, now(), now(), now())""",
                tie, t, stage, num, UUID.fromString(ta), UUID.fromString(tb), fin);
        String m1 = match(tie, 1, ms, adminId, a1, b1);
        String m2 = match(tie, 2, ms, adminId, a2, b2);

        // ---- Per-match rules before start: match 2 is one game to 11 (cap 15) ----
        JsonNode s2 = call(put("/api/admin/competition/matches/" + m2 + "/rules"),
                Map.of("pointsToWin", 11, "winBy", 2, "pointCap", 15, "gamesToWin", 1), 200);
        assertThat(s2.at("/rules/pointsToWin").asInt()).isEqualTo(11);
        assertThat(s2.at("/rules/custom").asBoolean()).isTrue();

        // ---- Scorer: start, idempotent taps, deuce, undo across games ----
        JsonNode mine = call(get("/api/scorer/matches"), null, 200);
        assertThat(mine.toString()).contains(m1, m2);
        call(post(S + m1 + "/point"), Map.of("side", "A", "clientEventId", "x"), 409); // not started
        JsonNode st = call(post(S + m1 + "/start"), Map.of("firstServer", "B", "clientEventId", UUID.randomUUID().toString()), 200);
        assertThat(st.get("status").asText()).isEqualTo("LIVE");
        assertThat(st.get("server").asText()).isEqualTo("B");
        String k = UUID.randomUUID().toString();
        tap(m1, "A", k);
        st = tap(m1, "A", k); // same tap retried
        assertThat(st.get("scoreA").asInt()).isEqualTo(1);
        assertThat(st.get("server").asText()).isEqualTo("A");
        for (int i = 0; i < 19; i++) {
            tap(m1, "A", UUID.randomUUID().toString());
            tap(m1, "B", UUID.randomUUID().toString());
        }
        st = taps(m1, "B", 1); // 20-20
        assertThat(st.get("scoreA").asInt()).isEqualTo(20);
        assertThat(st.get("scoreB").asInt()).isEqualTo(20);
        st = taps(m1, "A", 1); // 21-20, deuce continues
        assertThat(st.get("currentGame").asInt()).isEqualTo(1);
        st = taps(m1, "A", 1); // 22-20, game 1 to A
        assertThat(st.get("gamesA").asInt()).isEqualTo(1);
        assertThat(st.get("currentGame").asInt()).isEqualTo(2);
        st = call(post(S + m1 + "/undo"), ev(), 200); // back into game 1
        assertThat(st.get("currentGame").asInt()).isEqualTo(1);
        assertThat(st.get("scoreA").asInt()).isEqualTo(21);
        taps(m1, "A", 1);
        st = taps(m1, "A", 21);
        assertThat(st.get("winnerSide").asText()).isEqualTo("A");
        assertThat(st.at("/games/1/scoreA").asInt()).isEqualTo(21);
        call(post(S + m1 + "/point"), Map.of("side", "B", "clientEventId", UUID.randomUUID().toString()), 409);

        // ---- Two-step: submit, return, resubmit, confirm ----
        st = call(post(S + m1 + "/submit"), ev(), 200);
        assertThat(st.get("status").asText()).isEqualTo("SUBMITTED");
        JsonNode live = call(get("/api/public/live"), null, null, null, 200);
        assertThat(live.toString()).contains(m1, "\"provisional\":true");
        assertThat(call(get("/api/admin/results/pending"), null, 200).toString()).contains(m1);
        call(post("/api/admin/results/matches/" + m1 + "/return"), Map.of("reason", " "), 422);
        st = call(post("/api/admin/results/matches/" + m1 + "/return"), Map.of("reason", "Check game 2"), 200);
        assertThat(st.get("status").asText()).isEqualTo("LIVE");
        assertThat(st.get("returnedReason").asText()).isEqualTo("Check game 2");
        call(post(S + m1 + "/submit"), ev(), 200);
        st = call(post("/api/admin/results/matches/" + m1 + "/confirm"), null, 200);
        assertThat(st.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(fixture(tie).get("status").asText()).isEqualTo("LIVE");

        // ---- Match 2: manual entry under its own rules ----
        call(post(S + m2 + "/manual"), Map.of("games", List.of(List.of(11, 10)), "firstServer", "A",
                "clientEventId", UUID.randomUUID().toString()), 422);
        st = call(post(S + m2 + "/manual"), Map.of("games", List.of(List.of(9, 11)), "firstServer", "A",
                "clientEventId", UUID.randomUUID().toString()), 200);
        assertThat(st.get("winnerSide").asText()).isEqualTo("B");
        call(post(S + m2 + "/submit"), ev(), 200);
        call(post("/api/admin/results/matches/" + m2 + "/confirm"), null, 200);

        // Tie 1–1 in matches; A wins on game difference (+2 vs −1) and moves into the final
        JsonNode f = fixture(tie);
        assertThat(f.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(f.get("matchesWonA").asInt()).isEqualTo(1);
        assertThat(f.get("result").asText()).isEqualTo("A");
        assertThat(fixture(fin.toString()).at("/teamA/id").asText()).isEqualTo(ta);
        JsonNode pub = call(get("/api/public/fixtures"), null, null, null, 200);
        assertThat(pub.toString()).contains("\"result\":\"A\"");

        // ---- Correction flips match 1 → B wins the tie 2–0 and takes the final slot ----
        call(post("/api/admin/results/matches/" + m1 + "/correct"),
                Map.of("games", List.of(List.of(15, 21), List.of(15, 21)), "outcome", "NORMAL"), 422);
        st = call(post("/api/admin/results/matches/" + m1 + "/correct"),
                Map.of("games", List.of(List.of(15, 21), List.of(15, 21)), "outcome", "NORMAL", "reason", "Sheet check"), 200);
        assertThat(st.get("winnerSide").asText()).isEqualTo("B");
        f = fixture(tie);
        assertThat(f.get("result").asText()).isEqualTo("B");
        assertThat(fixture(fin.toString()).at("/teamA/id").asText()).isEqualTo(tb);
        Integer audits = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'match.result.corrected' AND entity_id = ?",
                Integer.class, m1);
        assertThat(audits).isEqualTo(1);

        // Staff only
        call(get(S + m1), null, null, null, 401);

        // Leave the edition's fixtures as we found them (other stories regenerate fixtures)
        jdbc.update("DELETE FROM team_fixtures WHERE id = ?", tie);
        jdbc.update("DELETE FROM team_fixtures WHERE id = ?", fin);
        jdbc.update("DELETE FROM stages WHERE id = ?", stage);
    }

    JsonNode fixture(Object id) throws Exception {
        return call(get("/api/admin/competition/fixtures/" + id), null, 200);
    }

    String match(UUID fixture, int order, String category, String scorer, String a, String b) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO matches (id, fixture_id, match_order, category_id, players_per_side, scorer_user_id, status,
                  scheduled_at, created_at, updated_at) VALUES (?, ?, ?, ?, 1, ?, 'SCHEDULED', now(), now(), now())""",
                id, fixture, order, UUID.fromString(category), UUID.fromString(scorer));
        UUID teamA = jdbc.queryForObject("SELECT team_a_id FROM team_fixtures WHERE id = ?", UUID.class, fixture);
        UUID teamB = jdbc.queryForObject("SELECT team_b_id FROM team_fixtures WHERE id = ?", UUID.class, fixture);
        jdbc.update("INSERT INTO match_players (id, match_id, side, position, team_id, tournament_player_id) VALUES (?, ?, 'A', 1, ?, ?)",
                UUID.randomUUID(), id, teamA, UUID.fromString(a));
        jdbc.update("INSERT INTO match_players (id, match_id, side, position, team_id, tournament_player_id) VALUES (?, ?, 'B', 1, ?, ?)",
                UUID.randomUUID(), id, teamB, UUID.fromString(b));
        return id.toString();
    }

    String team(String name) throws Exception {
        return call(post("/api/admin/teams"), Map.of("name", name), 201).get("id").asText();
    }

    /** Registers and approves a player, then puts them in the team's squad directly. */
    String squadPlayer(String teamId, String name, String gender, String digits) throws Exception {
        String email = "score" + digits + "@example.com";
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
