package lk.ngbcl.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lk.ngbcl.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** M3: announcements, sponsors and committee — admin rules and what the public sees. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ContentIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    String admin;

    @BeforeEach
    void signIn() throws Exception {
        var r = mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Test-Admin-Password-1\"}")).andReturn();
        admin = json.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    JsonNode call(MockHttpServletRequestBuilder b, String bearer, Object body, int expected) throws Exception {
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        var r = mvc.perform(b).andReturn();
        String s = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).as(s).isEqualTo(expected);
        return s.isEmpty() ? null : json.readTree(s);
    }

    static String rand() {
        return Long.toString(ThreadLocalRandom.current().nextLong(100_000, 999_999));
    }

    @Test
    void draftsAndScheduledNewsStayHiddenUntilLive() throws Exception {
        String n = rand();
        JsonNode draft = call(post("/api/admin/content/announcements"), admin,
                Map.of("title", "Draft " + n, "body", "Not yet.", "status", "DRAFT"), 201);
        Map<String, Object> sched = new HashMap<>(Map.of("title", "Later " + n, "body", "Soon.", "status", "PUBLISHED"));
        sched.put("publishAt", Instant.now().plus(2, ChronoUnit.DAYS).toString());
        JsonNode scheduled = call(post("/api/admin/content/announcements"), admin, sched, 201);
        assertThat(scheduled.get("displayStatus").asText()).isEqualTo("SCHEDULED");
        JsonNode live = call(post("/api/admin/content/announcements"), admin,
                Map.of("title", "Live " + n, "body", "Draw on **Friday**. [Details](https://example.org)", "status", "PUBLISHED"), 201);

        String list = mvc.perform(get("/api/public/news?size=50")).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("Live " + n).doesNotContain("Draft " + n).doesNotContain("Later " + n);
        call(get("/api/public/news/" + draft.get("slug").asText()), null, null, 404);
        call(get("/api/public/news/" + scheduled.get("slug").asText()), null, null, 404);
        JsonNode article = call(get("/api/public/news/" + live.get("slug").asText()), null, null, 200);
        assertThat(article.get("body").asText()).contains("**Friday**");

        // Publish the draft; archive the live one
        Map<String, Object> pub = new HashMap<>(Map.of("title", "Draft " + n, "body", "Now.", "status", "PUBLISHED"));
        pub.put("version", draft.get("version").asLong());
        call(patch("/api/admin/content/announcements/" + draft.get("id").asText()), admin, pub, 200);
        Map<String, Object> arch = new HashMap<>(Map.of("title", "Live " + n, "body", "x", "status", "ARCHIVED"));
        arch.put("version", live.get("version").asLong());
        call(patch("/api/admin/content/announcements/" + live.get("id").asText()), admin, arch, 200);
        list = mvc.perform(get("/api/public/news?size=50")).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("Draft " + n).doesNotContain("Live " + n);

        // Validation and permission
        JsonNode bad = call(post("/api/admin/content/announcements"), admin, Map.of("title", " ", "body", ""), 400);
        assertThat(bad.get("errors").toString()).contains("title", "body");
        call(post("/api/admin/content/announcements"), null, Map.of("title", "x", "body", "y"), 401);
    }

    @Test
    void onlyOneActiveTitleSponsorAndInactiveOnesAreHidden() throws Exception {
        String n = rand();
        JsonNode t1 = call(post("/api/admin/content/sponsors"), admin, Map.of("name", "Title " + n, "tier", "TITLE",
                "active", true, "showOnWall", true, "showOnTicker", true), 201);
        JsonNode dup = call(post("/api/admin/content/sponsors"), admin, Map.of("name", "Title2 " + n, "tier", "TITLE",
                "active", true, "showOnWall", true, "showOnTicker", true), 400);
        assertThat(dup.get("errors").toString()).contains("tier");
        Map<String, Object> off = new HashMap<>(Map.of("name", "Title " + n, "tier", "TITLE", "active", false,
                "showOnWall", true, "showOnTicker", true));
        off.put("version", t1.get("version").asLong());
        call(patch("/api/admin/content/sponsors/" + t1.get("id").asText()), admin, off, 200);
        String pub = mvc.perform(get("/api/public/sponsors")).andReturn().getResponse().getContentAsString();
        assertThat(pub).doesNotContain("Title " + n);
        call(post("/api/admin/content/sponsors"), admin, Map.of("name", "Bad link " + n, "tier", "GOLD",
                "websiteUrl", "javascript:alert(1)", "active", true), 400);
    }

    @Test
    void committeeGroupsShowOnlyVisibleMembers() throws Exception {
        String n = rand();
        JsonNode groups = call(post("/api/admin/content/committee/groups"), admin, Map.of("name", "Group " + n), 201);
        String gid = groups.get(groups.size() - 1).get("id").asText();
        call(post("/api/admin/content/committee/members"), admin,
                Map.of("groupId", gid, "fullName", "Shown " + n, "role", "Treasurer", "visible", true), 201);
        call(post("/api/admin/content/committee/members"), admin,
                Map.of("groupId", gid, "fullName", "Hidden " + n, "role", "Helper", "visible", false), 201);
        String pub = mvc.perform(get("/api/public/committee")).andReturn().getResponse().getContentAsString();
        assertThat(pub).contains("Shown " + n).doesNotContain("Hidden " + n);
        // A group with members can't be deleted
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/admin/content/committee/groups/" + gid), admin, null, 409);
    }
}
