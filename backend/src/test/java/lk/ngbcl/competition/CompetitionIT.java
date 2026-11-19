package lk.ngbcl.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * M5: template and format rules, group draw, generation (groups + knockout with 3rd place),
 * scheduling conflicts (FIX-009), line-up rules (FIX-008, gender, per-tie limit) and publishing
 * a frozen public copy (FIX-010). One story because the edition has one competition.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CompetitionIT {

    static final String C = "/api/admin/competition";

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
    void competitionFromTemplateToPublishedFixtures() throws Exception {
        var login = mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Test-Admin-Password-1\"}")).andReturn();
        admin = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        openWindows();
        String n = Long.toString(ThreadLocalRandom.current().nextLong(10_000, 99_999));

        // ---- Seeded defaults, template and categories ----
        JsonNode setup = call(get(C + "/setup"), null, 200);
        assertThat(setup.get("categories").size()).isGreaterThanOrEqualTo(5);
        assertThat(setup.get("courts").size()).isGreaterThanOrEqualTo(3);
        Map<String, String> cat = new HashMap<>();
        setup.get("categories").forEach(c -> cat.put(c.get("code").asText(), c.get("id").asText()));
        call(post(C + "/categories"), Map.of("code", "x d", "name", "Bad", "playersPerSide", 2), 400);
        call(post(C + "/categories"), Map.of("code", "MX", "name", "Mixed single", "playersPerSide", 1,
                "genderRule", "MIXED"), 400);
        setup = call(put(C + "/template"), Map.of("categoryIds", List.of(cat.get("MS"), cat.get("MD"), cat.get("XD"))), 200);
        assertThat(setup.get("template").size()).isEqualTo(3);
        assertThat(setup.at("/template/2/code").asText()).isEqualTo("XD");

        Map<String, Object> s = new HashMap<>();
        s.put("format", "GROUPS_KNOCKOUT");
        s.put("groupCount", 2);
        s.put("qualifiersPerGroup", 3);
        s.put("thirdPlaceMatch", true);
        s.put("maxMatchesPerPlayer", 2);
        s.put("matchMinutes", 40);
        s.put("restMinutes", 0);
        assertThat(call(put(C + "/settings"), s, 400).get("errors").toString()).contains("qualifiersPerGroup");
        s.put("qualifiersPerGroup", 2);
        call(put(C + "/settings"), s, 200);

        // ---- Teams with squads (men and women) ----
        String ta = team("Comp A " + n);
        String tb = team("Comp B " + n);
        team("Comp C " + n);
        team("Comp D " + n);
        String a1 = squadPlayer(ta, "Arun M " + n, "MALE", n + "1");
        String a2 = squadPlayer(ta, "Bala M " + n, "MALE", n + "2");
        String aw = squadPlayer(ta, "Chitra F " + n, "FEMALE", n + "3");
        String b1 = squadPlayer(tb, "Dinesh M " + n, "MALE", n + "4");
        squadPlayer(tb, "Elan M " + n, "MALE", n + "5");
        String bw = squadPlayer(tb, "Fathima F " + n, "FEMALE", n + "6");

        // ---- Group draw (snake: seeds 1 and 4 share group A) ----
        JsonNode groups = call(get(C + "/groups"), null, 200);
        List<String> seedOrder = new ArrayList<>();
        seedOrder.add(ta);
        List<String> others = new ArrayList<>();
        groups.get("unassigned").forEach(t -> {
            String id = t.get("id").asText();
            if (!id.equals(ta) && !id.equals(tb)) {
                others.add(id);
            }
        });
        groups.get("groups").forEach(g -> g.get("teams").forEach(t -> {
            String id = t.get("id").asText();
            if (!id.equals(ta) && !id.equals(tb) && !others.contains(id)) {
                others.add(id);
            }
        }));
        seedOrder.add(others.get(0));
        seedOrder.add(others.get(1));
        seedOrder.add(tb);
        seedOrder.addAll(others.subList(2, others.size()));
        groups = call(post(C + "/groups/draw"), Map.of("teamIds", seedOrder), 200);
        assertThat(groups.at("/groups/0/teams").toString()).contains(ta, tb);

        // ---- Generate ----
        JsonNode view = call(post(C + "/fixtures/generate"), null, 200);
        JsonNode fixtures = view.get("fixtures");
        JsonNode tie = find(fixtures, ta, tb);
        assertThat(tie.get("matches").size()).isEqualTo(3);
        assertThat(tie.get("groupName").asText()).isEqualTo("Group A");
        List<JsonNode> ko = new ArrayList<>();
        fixtures.forEach(f -> {
            if ("KNOCKOUT".equals(f.get("stageType").asText())) {
                ko.add(f);
            }
        });
        assertThat(ko).hasSize(4); // 2 semis, final, 3rd place
        assertThat(ko.get(0).get("sourceA").asText()).isEqualTo("Group A #1");
        assertThat(ko.get(0).get("sourceB").asText()).isEqualTo("Group B #2");
        JsonNode fin = ko.stream().filter(f -> f.get("roundName").asText().equals("Final")).findFirst().orElseThrow();
        assertThat(fin.get("sourceA").asText()).isEqualTo("Winner of SF1");
        JsonNode third = ko.stream().filter(f -> f.get("roundName").asText().equals("3rd place")).findFirst().orElseThrow();
        assertThat(third.get("sourceB").asText()).isEqualTo("Loser of SF2");
        call(put(C + "/groups"), Map.of("groups", List.of(List.of(), List.of())), 409);
        call(put(C + "/fixtures/" + fin.get("id").asText() + "/teams"), Map.of("teamAId", ta, "teamBId", ta), 422);

        // ---- Line-ups ----
        String fid = tie.get("id").asText();
        String ms = tie.at("/matches/0/id").asText();
        String md = tie.at("/matches/1/id").asText();
        String xd = tie.at("/matches/2/id").asText();
        String lu = C + "/fixtures/" + fid + "/matches/";
        boolean aIsA = tie.at("/teamA/id").asText().equals(ta);
        call(put(lu + ms + "/lineup"), sides(aIsA, List.of(aw), List.of(b1)), 400);  // women in men's singles
        call(put(lu + ms + "/lineup"), sides(aIsA, List.of(b1), List.of(b1)), 400);  // wrong team
        call(put(lu + ms + "/lineup"), sides(aIsA, List.of(a1, a2), List.of(b1)), 400); // singles needs one
        call(put(lu + ms + "/lineup"), sides(aIsA, List.of(a1), List.of(b1)), 200);
        call(put(lu + md + "/lineup"), sides(aIsA, List.of(a1, a2), List.of()), 200);
        call(put(lu + xd + "/lineup"), sides(aIsA, List.of(a1, aw), List.of(b1, bw)), 422); // a1 would play 3
        call(put(lu + xd + "/lineup"), sides(aIsA, List.of(a2, a2), List.of(b1, bw)), 400); // same player twice
        Map<String, Object> xdBody = sides(aIsA, List.of(a2, aw), List.of(b1, bw));
        String scorer = view.at("/scorers/0/id").asText();
        xdBody.put("scorerUserId", scorer);
        tie = call(put(lu + xd + "/lineup"), xdBody, 200);
        assertThat(tie.at("/matches/2/scorer/id").asText()).isEqualTo(scorer);
        assertThat(tie.at("/matches/2/lineupComplete").asBoolean()).isTrue();
        call(put(lu + ms + "/lineup"), sides(aIsA, List.of(a2), List.of(b1)), 422); // a2 already plays MD and XD

        // ---- Scheduling conflicts (FIX-009) ----
        String court1 = view.at("/courts/0/id").asText();
        String court2 = view.at("/courts/1/id").asText();
        Instant at = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        tie = call(put(C + "/fixtures/" + fid + "/schedule"), sched(at, court1, tie.get("version").asLong()), 200);
        assertThat(tie.get("endsAt").asText()).isEqualTo(at.plus(120, ChronoUnit.MINUTES).toString());
        JsonNode otherA = sameTeamOther(fixtures, ta, tb);
        JsonNode groupB = groupFixture(fixtures, "Group B");
        assertThat(call(put(C + "/fixtures/" + otherA.get("id").asText() + "/schedule"),
                sched(at.plus(60, ChronoUnit.MINUTES), court2, null), 409).get("code").asText()).isEqualTo("SCHEDULE_CONFLICT");
        assertThat(call(put(C + "/fixtures/" + groupB.get("id").asText() + "/schedule"),
                sched(at.plus(30, ChronoUnit.MINUTES), court1, null), 409).get("detail").asText()).contains("court");
        call(put(C + "/fixtures/" + groupB.get("id").asText() + "/schedule"), sched(at, court2, null), 200);
        call(put(C + "/fixtures/" + otherA.get("id").asText() + "/schedule"),
                sched(at.plus(120, ChronoUnit.MINUTES), court1, null), 200);

        // ---- Publish: frozen public copy ----
        JsonNode preview = call(get(C + "/fixtures/publish-preview"), null, 200);
        assertThat(preview.get("items").size()).isEqualTo(fixtures.size());
        call(post(C + "/fixtures/publish"), null, 200);
        call(post(C + "/fixtures/publish"), null, 409);
        JsonNode pub = call(get("/api/public/fixtures"), null, null, null, 200);
        assertThat(pub.get("fixtures").size()).isEqualTo(fixtures.size());
        assertThat(pub.toString()).contains("Comp A " + n, "Chitra F " + n).doesNotContain("@example.com");
        assertThat(pub.toString()).doesNotContain("FEMALE"); // no gender in public data

        // A change stays private until republished
        Instant later = at.plus(1, ChronoUnit.DAYS);
        tie = call(get(C + "/fixtures/" + fid), null, 200);
        tie = call(put(C + "/fixtures/" + fid + "/schedule"), sched(later, court1, tie.get("version").asLong()), 200);
        assertThat(tie.get("changedSincePublish").asBoolean()).isTrue();
        assertThat(call(get("/api/public/fixtures"), null, null, null, 200).toString()).doesNotContain(later.toString());
        preview = call(get(C + "/fixtures/publish-preview"), null, 200);
        assertThat(preview.at("/items/0/change").asText()).isEqualTo("CHANGED");
        call(post(C + "/fixtures/publish"), Map.of("fixtureIds", List.of(fid)), 200);
        assertThat(call(get("/api/public/fixtures"), null, null, null, 200).toString()).contains(later.toString());

        // Published fixtures can't be regenerated or deleted
        call(post(C + "/fixtures/generate"), null, 409);
        call(delete(C + "/fixtures"), null, 409);

        // Staff only
        call(get(C + "/fixtures"), null, null, null, 401);
    }

    // ---- helpers -------------------------------------------------------------------------------

    static Map<String, Object> sides(boolean teamAIsSideA, List<String> mine, List<String> theirs) {
        Map<String, Object> m = new HashMap<>();
        m.put("sideA", teamAIsSideA ? mine : theirs);
        m.put("sideB", teamAIsSideA ? theirs : mine);
        return m;
    }

    static Map<String, Object> sched(Instant at, String court, Long version) {
        Map<String, Object> m = new HashMap<>();
        m.put("scheduledAt", at.toString());
        m.put("courtId", court);
        m.put("version", version);
        return m;
    }

    static JsonNode find(JsonNode fixtures, String x, String y) {
        for (JsonNode f : fixtures) {
            String a = f.at("/teamA/id").asText();
            String b = f.at("/teamB/id").asText();
            if ((a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x))) {
                return f;
            }
        }
        throw new AssertionError("no fixture " + x + " v " + y);
    }

    static JsonNode sameTeamOther(JsonNode fixtures, String team, String exclude) {
        for (JsonNode f : fixtures) {
            String a = f.at("/teamA/id").asText();
            String b = f.at("/teamB/id").asText();
            if ((a.equals(team) || b.equals(team)) && !a.equals(exclude) && !b.equals(exclude)) {
                return f;
            }
        }
        throw new AssertionError("no other fixture for " + team);
    }

    static JsonNode groupFixture(JsonNode fixtures, String group) {
        for (JsonNode f : fixtures) {
            if (group.equals(f.path("groupName").asText())) {
                return f;
            }
        }
        throw new AssertionError("no fixture in " + group);
    }

    String team(String name) throws Exception {
        return call(post("/api/admin/teams"), Map.of("name", name), 201).get("id").asText();
    }

    /** Registers and approves a player, then puts them in the team's squad directly. */
    String squadPlayer(String teamId, String name, String gender, String digits) throws Exception {
        String email = "comp" + digits + "@example.com";
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
