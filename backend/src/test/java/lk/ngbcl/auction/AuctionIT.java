package lk.ngbcl.auction;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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
 * M4: the whole auction in one story — settings, queue, bidding rules, idempotent retries,
 * concurrent bidding, sales, unsold, re-auction with a hand-picked unsold list, corrections,
 * and what the public sees. One test because the edition has exactly one auction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuctionIT {

    static final String A = "/api/admin/auction";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    InMemoryObjectStorage storage;

    @Autowired
    JdbcTemplate jdbc;

    String admin;

    JsonNode call(MockHttpServletRequestBuilder b, String bearer, Object body, int expected) throws Exception {
        return call(b, bearer, null, body, expected);
    }

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

    int status(MockHttpServletRequestBuilder b, Object body) throws Exception {
        b.header("Authorization", "Bearer " + admin);
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }

    JsonNode bid(String lotId, String teamId, long amount, String key, int expected) throws Exception {
        return call(post(A + "/bids").header("Idempotency-Key", key), admin,
                Map.of("lotId", lotId, "teamId", teamId, "amount", amount), expected);
    }

    static String key() {
        return UUID.randomUUID().toString();
    }

    @Test
    void fullAuction() throws Exception {
        var login = mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Test-Admin-Password-1\"}")).andReturn();
        admin = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        openWindows();
        String n = Long.toString(ThreadLocalRandom.current().nextLong(10_000, 99_999));

        // ---- Teams and players ----
        String teamA = team("Auction A " + n);
        String teamB = team("Auction B " + n);
        String teamC = team("Auction C " + n);
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            names.add("Lot Player " + i + " " + n);
            approvedPlayer(names.get(i - 1), n + i);
        }

        // ---- Settings (before start: everything editable) ----
        JsonNode settings = call(get(A + "/settings"), admin, null, 200);
        assertThat(settings.get("locked").asBoolean()).isFalse();
        Map<String, Object> s = new HashMap<>();
        s.put("initialBudget", 100_000);
        s.put("maxSquadSize", 2);
        s.put("defaultBasePrice", 10_000);
        s.put("minIncrement", 5_000);
        s.put("allowReauction", true);
        s.put("allowManualAdjustment", true);
        s.put("version", settings.path("version").isNumber() ? settings.get("version").asLong() : null);
        settings = call(put(A + "/settings"), admin, s, 200);
        Map<String, Object> bad = new HashMap<>(s);
        bad.put("defaultBasePrice", 0);
        bad.put("version", settings.get("version").asLong());
        assertThat(call(put(A + "/settings"), admin, bad, 400).get("errors").toString()).contains("defaultBasePrice");

        // ---- Queue follows approved players; per-player base price ----
        JsonNode snap = call(get(A), admin, null, 200);
        assertThat(snap.get("status").asText()).isEqualTo("NOT_STARTED");
        Map<String, JsonNode> lot = lotsByName(snap);
        assertThat(lot.keySet()).containsAll(names);
        String p1Entry = lot.get(names.get(0)).get("entryId").asText();
        snap = call(put(A + "/players/" + p1Entry + "/base-price"), admin, Map.of("basePrice", 20_000), 200);
        assertThat(lotsByName(snap).get(names.get(0)).get("basePrice").asLong()).isEqualTo(20_000);
        assertThat(lotsByName(snap).get(names.get(0)).get("basePriceOverridden").asBoolean()).isTrue();
        assertThat(lotsByName(snap).get(names.get(1)).get("basePrice").asLong()).isEqualTo(10_000);

        // Players seeded by other tests leave the queue so this story controls the rounds
        for (JsonNode q : snap.get("queue")) {
            if (!names.contains(q.get("fullName").asText()) && "QUEUED".equals(q.get("state").asText())) {
                call(post(A + "/lots/" + q.get("lotId").asText() + "/withdraw"), admin,
                        Map.of("reason", "Not part of this test"), 200);
            }
        }
        String l1 = lot.get(names.get(0)).get("lotId").asText();
        String l2 = lot.get(names.get(1)).get("lotId").asText();
        String l3 = lot.get(names.get(2)).get("lotId").asText();
        String l4 = lot.get(names.get(3)).get("lotId").asText();
        String l5 = lot.get(names.get(4)).get("lotId").asText();

        // Commands before the start are refused
        call(post(A + "/lots/open"), admin, Map.of("lotId", l1), 409);

        // ---- Start ----
        snap = call(post(A + "/start"), admin, null, 200);
        assertThat(snap.get("status").asText()).isEqualTo("LIVE");
        call(post(A + "/start"), admin, null, 409);
        assertThat(rail(snap, teamA).get("remaining").asLong()).isEqualTo(100_000);

        // Locked settings: budget can't change, other changes need a reason
        settings = call(get(A + "/settings"), admin, null, 200);
        assertThat(settings.get("locked").asBoolean()).isTrue();
        Map<String, Object> locked = new HashMap<>(s);
        locked.put("initialBudget", 200_000);
        locked.put("version", settings.get("version").asLong());
        assertThat(call(put(A + "/settings"), admin, locked, 400).get("errors").toString())
                .contains("initialBudget", "reason");

        // ---- Lot 1: rules, idempotency, undo, sale ----
        snap = call(post(A + "/lots/open"), admin, Map.of("lotId", l1), 200);
        assertThat(snap.at("/currentLot/id").asText()).isEqualTo(l1);
        assertThat(snap.at("/currentLot/lotNumber").asInt()).isEqualTo(1);
        call(post(A + "/lots/open"), admin, Map.of("lotId", l2), 409);
        assertThat(bid(l1, teamA, 15_000, key(), 409).get("code").asText()).isEqualTo("BID_TOO_LOW");
        String k1 = key();
        JsonNode first = bid(l1, teamA, 20_000, k1, 201);
        JsonNode replay = bid(l1, teamA, 20_000, k1, 201);
        assertThat(replay.at("/bid/replayed").asBoolean()).isTrue();
        assertThat(replay.at("/bid/bidId").asText()).isEqualTo(first.at("/bid/bidId").asText());
        assertThat(bid(l1, teamB, 21_000, k1, 409).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(bid(l1, teamA, 25_000, key(), 409).get("code").asText()).isEqualTo("ALREADY_LEADING");
        assertThat(bid(l1, teamB, 24_000, key(), 409).get("code").asText()).isEqualTo("BID_TOO_LOW");
        bid(l1, teamB, 25_000, key(), 201);
        snap = call(post(A + "/bids/undo"), admin, Map.of("lotId", l1), 200);
        assertThat(snap.at("/currentLot/leadingTeamId").asText()).isEqualTo(teamA);
        assertThat(snap.at("/currentLot/currentAmount").asLong()).isEqualTo(20_000);
        String aBid = first.at("/bid/bidId").asText();
        String bBid = bid(l1, teamB, 30_000, key(), 201).at("/bid/bidId").asText();
        assertThat(call(post(A + "/lots/sell"), admin, Map.of("lotId", l1, "bidId", aBid), 409).get("code").asText())
                .isEqualTo("LEAD_CHANGED");
        snap = call(post(A + "/lots/sell"), admin, Map.of("lotId", l1, "bidId", bBid), 200);
        assertThat(snap.path("currentLot").isNull()).isTrue();
        assertThat(rail(snap, teamB).get("remaining").asLong()).isEqualTo(70_000);
        assertThat(rail(snap, teamB).get("squadCount").asInt()).isEqualTo(1);

        // ---- Lot 2: no bids → unsold ----
        call(post(A + "/lots/open"), admin, Map.of("lotId", l2), 200);
        call(post(A + "/lots/sell"), admin, Map.of("lotId", l2, "bidId", UUID.randomUUID().toString()), 409);
        snap = call(post(A + "/lots/unsold"), admin, Map.of("lotId", l2), 200);
        assertThat(lotsByName(snap).get(names.get(1)).get("state").asText()).isEqualTo("UNSOLD");

        // ---- Lot 3: two bidders hammering the console at once ----
        call(post(A + "/lots/open"), admin, Map.of("lotId", l3), 200);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> jobs = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            String team = t % 2 == 0 ? teamA : teamC;
            jobs.add(pool.submit(() -> {
                go.await();
                for (int i = 0; i < 6; i++) {
                    JsonNode cur = call(get(A), admin, null, 200).get("currentLot");
                    if (team.equals(cur.path("leadingTeamId").asText()) || cur.get("nextMinimum").asLong() > 40_000) {
                        continue;
                    }
                    int st = status(post(A + "/bids").header("Idempotency-Key", key()),
                            Map.of("lotId", l3, "teamId", team, "amount", cur.get("nextMinimum").asLong()));
                    assertThat(st).isIn(201, 409, 422);
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : jobs) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        snap = call(get(A), admin, null, 200);
        JsonNode ladder = snap.at("/currentLot/bids");
        assertThat(ladder.size()).isGreaterThan(1);
        long prevAmount = Long.MAX_VALUE;
        String prevTeam = null;
        for (JsonNode b : ladder) { // newest first
            assertThat(b.get("amount").asLong()).isLessThanOrEqualTo(prevAmount - 5_000);
            assertThat(b.get("teamId").asText()).isNotEqualTo(prevTeam);
            prevAmount = b.get("amount").asLong();
            prevTeam = b.get("teamId").asText();
        }
        assertThat(snap.at("/currentLot/currentAmount").asLong()).isEqualTo(ladder.get(0).get("amount").asLong());
        String l3Winner = snap.at("/currentLot/leadingTeamId").asText();
        call(post(A + "/lots/sell"), admin, Map.of("lotId", l3, "bidId", ladder.get(0).get("id").asText()), 200);

        // ---- Lot 4 fills B's squad; B can't bid on lot 5 ----
        call(post(A + "/lots/open"), admin, Map.of("lotId", l4), 200);
        String b4 = bid(l4, teamB, 10_000, key(), 201).at("/bid/bidId").asText();
        call(post(A + "/lots/sell"), admin, Map.of("lotId", l4, "bidId", b4), 200);
        snap = call(post(A + "/lots/open"), admin, null, 200); // next in queue = lot 5
        assertThat(snap.at("/currentLot/id").asText()).isEqualTo(l5);
        assertThat(rail(snap, teamB).get("canBid").asBoolean()).isFalse();
        assertThat(bid(l5, teamB, 10_000, key(), 422).get("code").asText()).isEqualTo("SQUAD_FULL");
        snap = call(post(A + "/lots/unsold"), admin, Map.of("lotId", l5), 200);

        // ---- Next round with a hand-picked unsold player ----
        assertThat(snap.get("canStartNextRound").asBoolean()).isTrue();
        snap = call(post(A + "/rounds/next"), admin, Map.of("lotIds", List.of(l5)), 200);
        assertThat(snap.get("round").asInt()).isEqualTo(2);
        assertThat(lotsByName(snap).get(names.get(4)).get("state").asText()).isEqualTo("QUEUED");
        assertThat(lotsByName(snap).get(names.get(1)).get("state").asText()).isEqualTo("UNSOLD");
        call(post(A + "/rounds/next"), admin, null, 409); // round 2 not finished
        String other = l3Winner.equals(teamA) ? teamC : teamA;
        call(post(A + "/lots/open"), admin, null, 200);
        String b5 = bid(l5, other, 10_000, key(), 201).at("/bid/bidId").asText();
        call(post(A + "/lots/sell"), admin, Map.of("lotId", l5, "bidId", b5), 200);

        // ---- Public view while live ----
        JsonNode pub = call(get("/api/public/auction/state"), null, null, 200);
        assertThat(pub.get("status").asText()).isEqualTo("LIVE");
        assertThat(pub.at("/lastClosed/fullName").asText()).isEqualTo(names.get(4));
        assertThat(pub.toString()).doesNotContain("@example.com");

        // ---- Corrections ----
        call(post(A + "/lots/" + l1 + "/release"), admin, Map.of("reason", " "), 422);
        JsonNode rosters = call(post(A + "/lots/" + l1 + "/release"), admin, Map.of("reason", "Player withdrew"), 200);
        JsonNode bRoster = roster(rosters, teamB);
        assertThat(bRoster.at("/team/remaining").asLong()).isEqualTo(90_000);
        assertThat(bRoster.at("/team/squadCount").asInt()).isEqualTo(1);
        long cBefore = roster(rosters, teamC).at("/team/remaining").asLong();
        rosters = call(post(A + "/lots/" + l4 + "/move"), admin,
                Map.of("teamId", teamC, "price", 12_000, "reason", "Recorded against the wrong team"), 200);
        assertThat(roster(rosters, teamB).at("/team/squadCount").asInt()).isZero();
        assertThat(roster(rosters, teamB).at("/team/remaining").asLong()).isEqualTo(100_000);
        assertThat(roster(rosters, teamC).at("/team/remaining").asLong()).isEqualTo(cBefore - 12_000);
        assertThat(roster(rosters, teamC).at("/team/squadCount").asInt()).isEqualTo(2);
        // C is now full: moving another sale there is refused
        call(post(A + "/lots/" + l3 + "/move"), admin,
                Map.of("teamId", teamC, "reason", "Try"), teamC.equals(l3Winner) ? 200 : 422);
        call(post(A + "/teams/" + teamA + "/adjust"), admin, Map.of("amount", 5_000), 422);
        rosters = call(post(A + "/teams/" + teamA + "/adjust"), admin, Map.of("amount", 5_000, "reason", "Sponsor top-up"), 200);
        assertThat(roster(rosters, teamA).at("/team/adjustments").asLong()).isEqualTo(5_000);

        // Money always balances and never goes negative
        Integer broken = jdbc.queryForObject("""
                SELECT count(*) FROM team_budgets
                WHERE remaining < 0 OR spent < 0 OR spent + remaining <> initial_budget + adjustments
                """, Integer.class);
        assertThat(broken).isZero();
        Integer doubleOwned = jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT tournament_player_id FROM team_players WHERE active
                GROUP BY tournament_player_id HAVING count(*) > 1) x
                """, Integer.class);
        assertThat(doubleOwned).isZero();

        // ---- Pause, end, results ----
        call(post(A + "/pause"), admin, null, 200);
        call(post(A + "/lots/open"), admin, null, 409);
        call(post(A + "/resume"), admin, null, 200);
        snap = call(post(A + "/end"), admin, Map.of("reason", "Done"), 200);
        assertThat(snap.get("status").asText()).isEqualTo("COMPLETED");
        JsonNode results = call(get("/api/public/auction/results"), null, null, 200);
        assertThat(results.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(results.get("unsold").toString()).contains(names.get(1), names.get(0));
        assertThat(results.get("sold").toString()).contains(names.get(2), names.get(4));

        // The player page shows the team
        JsonNode players = call(get("/api/public/players"), null, null, 200);
        for (JsonNode p : players) {
            if (names.get(4).equals(p.get("fullName").asText())) {
                assertThat(p.get("teamName").asText()).startsWith("Auction ");
            }
        }

        // Staff only
        call(get(A), null, null, 401);
        call(post(A + "/bids").header("Idempotency-Key", key()), null,
                Map.of("lotId", l5, "teamId", teamA, "amount", 1), 401);
    }

    // ---- helpers -------------------------------------------------------------------------

    static Map<String, JsonNode> lotsByName(JsonNode snap) {
        Map<String, JsonNode> out = new HashMap<>();
        snap.get("queue").forEach(q -> out.put(q.get("fullName").asText(), q));
        return out;
    }

    static JsonNode rail(JsonNode snap, String teamId) {
        for (JsonNode t : snap.get("teams")) {
            if (teamId.equals(t.get("teamId").asText())) {
                return t;
            }
        }
        throw new AssertionError("team " + teamId + " not in rail");
    }

    static JsonNode roster(JsonNode rosters, String teamId) {
        for (JsonNode r : rosters) {
            if (teamId.equals(r.at("/team/teamId").asText())) {
                return r;
            }
        }
        throw new AssertionError("team " + teamId + " not in rosters");
    }

    String team(String name) throws Exception {
        return call(post("/api/admin/teams"), admin, Map.of("name", name), 201).get("id").asText();
    }

    void approvedPlayer(String name, String digits) throws Exception {
        String email = "lot" + digits + "@example.com";
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
        p.put("phone", "+9477" + String.format("%07d", Long.parseLong(digits) % 10_000_000));
        p.put("dateOfBirth", "1998-03-03");
        p.put("gender", "MALE");
        p.put("homeArea", "Nallur");
        p.put("emergencyName", "Contact " + digits);
        p.put("emergencyPhone", "+9471" + String.format("%07d", Long.parseLong(digits) % 10_000_000));
        p.put("photoAssetId", photoId);
        p.put("jaffnaDeclared", true);
        p.put("consent", true);
        call(post("/api/public/registration/player"), null, token, p, 201);
        String regId = call(get("/api/admin/registrations/players?status=PENDING&q=" + email), admin, null, 200)
                .at("/content/0/id").asText();
        call(post("/api/admin/registrations/players/" + regId + "/photo/verify"), admin, Map.of("approve", true), 200);
        call(post("/api/admin/registrations/players/" + regId + "/approve"), admin, Map.of(), 200);
    }

    void openWindows() throws Exception {
        JsonNode s = call(get("/api/admin/settings"), admin, null, 200);
        if ("DRAFT".equals(s.at("/general/status").asText())) {
            call(put("/api/admin/tournament/status"), admin, Map.of("status", "UPCOMING"), 200);
            s = call(get("/api/admin/settings"), admin, null, 200);
        }
        ObjectNode reg = (ObjectNode) s.get("registration").deepCopy();
        Instant now = Instant.now();
        reg.put("playerOpensAt", now.minus(1, ChronoUnit.DAYS).toString());
        reg.put("playerClosesAt", now.plus(10, ChronoUnit.DAYS).toString());
        reg.put("teamOpensAt", now.minus(1, ChronoUnit.DAYS).toString());
        reg.put("teamClosesAt", now.plus(10, ChronoUnit.DAYS).toString());
        reg.put("categoryRequired", false);
        reg.put("settingsVersion", s.get("settingsVersion").asLong());
        call(put("/api/admin/settings/registration"), admin, reg, 200);
    }
}
