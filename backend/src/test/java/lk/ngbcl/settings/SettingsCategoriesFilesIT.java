package lk.ngbcl.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lk.ngbcl.TestcontainersConfiguration;
import lk.ngbcl.files.InMemoryObjectStorage;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SettingsCategoriesFilesIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    InMemoryObjectStorage storage;

    String admin;

    @BeforeEach
    void signIn() throws Exception {
        var r = mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Test-Admin-Password-1\"}")).andReturn();
        admin = json.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode body(MockHttpServletRequestBuilder b, int expectedStatus) throws Exception {
        var r = mvc.perform(auth(b)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(expectedStatus);
        String s = r.getResponse().getContentAsString();
        return s.isEmpty() ? null : json.readTree(s);
    }

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void seededSettingsCarryLaunchPrizes() throws Exception {
        JsonNode s = body(get("/api/admin/settings"), 200);
        assertThat(s.at("/publicInfo/prizeFirst").asLong()).isEqualTo(300000);
        assertThat(s.at("/publicInfo/teamEntryFee").asLong()).isEqualTo(150000);
        assertThat(s.at("/registration/playerFields/homeArea/required").asBoolean()).isTrue();
    }

    @Test
    void registrationWindowDrivesPublicPhaseOnlyOnceTournamentIsNotDraft() throws Exception {
        JsonNode s = body(get("/api/admin/settings"), 200);
        ObjectNode reg = (ObjectNode) s.get("registration").deepCopy();
        Instant now = Instant.now();
        reg.put("playerOpensAt", now.minus(1, ChronoUnit.DAYS).toString());
        reg.put("playerClosesAt", now.plus(10, ChronoUnit.DAYS).toString());
        reg.put("settingsVersion", s.get("settingsVersion").asLong());
        JsonNode saved = body(put("/api/admin/settings/registration").content(reg.toString()), 200);

        // A draft keeps registration closed to the public. (Other test classes share this database
        // and may already have moved the tournament past DRAFT, so only assert it while still draft.)
        if ("DRAFT".equals(saved.at("/general/status").asText())) {
            assertThat(saved.at("/registration/playerState").asText()).isEqualTo("NOT_OPEN");
        }

        if ("DRAFT".equals(saved.at("/general/status").asText())) {
            body(put("/api/admin/tournament/status").content("{\"status\":\"UPCOMING\"}"), 200);
        }
        mvc.perform(get("/api/public/tournament"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerRegistration.state").value("OPEN"))
                .andExpect(jsonPath("$.phase").value("REGISTRATION"));
    }

    @Test
    void consecutiveSavesUseTheVersionReturnedByTheApi() throws Exception {
        JsonNode s = body(get("/api/admin/settings"), 200);
        for (int i = 0; i < 2; i++) {
            ObjectNode reg = (ObjectNode) s.get("registration").deepCopy();
            reg.put("notifyOnRegistration", i == 0);
            reg.put("settingsVersion", s.get("settingsVersion").asLong());
            s = body(put("/api/admin/settings/registration").content(reg.toString()), 200);
        }
        assertThat(s.at("/registration/notifyOnRegistration").asBoolean()).isFalse();
    }

    @Test
    void closingBeforeOpeningIsRejected() throws Exception {
        JsonNode s = body(get("/api/admin/settings"), 200);
        ObjectNode reg = (ObjectNode) s.get("registration").deepCopy();
        Instant now = Instant.now();
        reg.put("teamOpensAt", now.toString());
        reg.put("teamClosesAt", now.minus(1, ChronoUnit.HOURS).toString());
        reg.put("settingsVersion", s.get("settingsVersion").asLong());
        JsonNode err = body(put("/api/admin/settings/registration").content(reg.toString()), 400);
        assertThat(err.get("code").asText()).isEqualTo("WINDOW_INVALID");
    }

    @Test
    void staleSettingsVersionIsAConflict() throws Exception {
        JsonNode s = body(get("/api/admin/settings"), 200);
        ObjectNode reg = (ObjectNode) s.get("registration").deepCopy();
        reg.put("settingsVersion", s.get("settingsVersion").asLong() + 99);
        JsonNode err = body(put("/api/admin/settings/registration").content(reg.toString()), 409);
        assertThat(err.get("code").asText()).isEqualTo("STALE_DATA");
    }

    @Test
    void categoriesAreUniqueOrderableAndHiddenWhenInactive() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        JsonNode a = body(post("/api/admin/categories").content(json.writeValueAsString(Map.of("name", "Alpha " + suffix))), 201);
        JsonNode b = body(post("/api/admin/categories").content(json.writeValueAsString(Map.of("name", "Beta " + suffix))), 201);
        JsonNode dup = body(post("/api/admin/categories").content(json.writeValueAsString(Map.of("name", "alpha " + suffix))), 409);
        assertThat(dup.get("code").asText()).isEqualTo("CATEGORY_NAME_TAKEN");

        JsonNode all = body(get("/api/admin/categories"), 200);
        List<String> ids = new java.util.ArrayList<>();
        all.forEach(c -> ids.add(c.get("id").asText()));
        java.util.Collections.reverse(ids);
        JsonNode reordered = body(put("/api/admin/categories/order").content(json.writeValueAsString(Map.of("ids", ids))), 200);
        assertThat(reordered.get(0).get("id").asText()).isEqualTo(ids.get(0));

        // Reordering bumped the version (optimistic locking) — use the latest one.
        long currentVersion = 0;
        for (JsonNode c : reordered) {
            if (c.get("id").asText().equals(a.get("id").asText())) {
                currentVersion = c.get("version").asLong();
            }
        }
        // A stale version is refused…
        JsonNode stale = body(patch("/api/admin/categories/" + a.get("id").asText())
                .content(json.writeValueAsString(Map.of("active", false, "version", currentVersion - 1))), 409);
        assertThat(stale.get("code").asText()).isEqualTo("STALE_DATA");
        // …the current one works.
        body(patch("/api/admin/categories/" + a.get("id").asText())
                .content(json.writeValueAsString(Map.of("active", false, "version", currentVersion))), 200);
        String pub = mvc.perform(get("/api/public/categories")).andReturn().getResponse().getContentAsString();
        assertThat(pub).doesNotContain("Alpha " + suffix).contains("Beta " + suffix);
        assertThat(b.get("displayOrder").asInt()).isGreaterThan(a.get("displayOrder").asInt());
    }

    @Test
    void uploadIsCheckedFromRealBytesThenPublishedForBranding() throws Exception {
        JsonNode ticket = body(post("/api/files/uploads").content(json.writeValueAsString(Map.of(
                "purpose", "TOURNAMENT_LOGO", "filename", "logo.png", "contentType", "image/png", "size", 2048))), 201);
        storage.simulateUpload(ticket.get("uploadUrl").asText(), png(600, 300));
        JsonNode done = body(post("/api/files/" + ticket.get("assetId").asText() + "/complete"), 200);
        assertThat(done.get("uploadStatus").asText()).isEqualTo("UPLOADED");
        assertThat(done.get("width").asInt()).isEqualTo(600);

        JsonNode s = body(get("/api/admin/settings"), 200);
        body(put("/api/admin/settings/branding").content(json.writeValueAsString(Map.of(
                "logoAssetId", ticket.get("assetId").asText(), "settingsVersion", s.get("settingsVersion").asLong()))), 200);

        mvc.perform(get("/api/public/tournament"))
                .andExpect(jsonPath("$.branding.logoUrl").value(org.hamcrest.Matchers.startsWith("https://cdn.test/")));
        assertThat(storage.publicObjects).isNotEmpty();
    }

    @Test
    void fakeImageAndWrongRatioAreRejected() throws Exception {
        JsonNode t1 = body(post("/api/files/uploads").content(json.writeValueAsString(Map.of(
                "purpose", "TEAM_LOGO", "filename", "x.png", "contentType", "image/png", "size", 100))), 201);
        storage.simulateUpload(t1.get("uploadUrl").asText(), "<?php echo 1; ?>".getBytes());
        JsonNode e1 = body(post("/api/files/" + t1.get("assetId").asText() + "/complete"), 422);
        assertThat(e1.get("code").asText()).isEqualTo("FILE_TYPE_NOT_ALLOWED");

        JsonNode t2 = body(post("/api/files/uploads").content(json.writeValueAsString(Map.of(
                "purpose", "TEAM_LOGO", "filename", "wide.png", "contentType", "image/png", "size", 100))), 201);
        storage.simulateUpload(t2.get("uploadUrl").asText(), png(600, 300));
        JsonNode e2 = body(post("/api/files/" + t2.get("assetId").asText() + "/complete"), 422);
        assertThat(e2.get("code").asText()).isEqualTo("IMAGE_WRONG_RATIO");

        body(post("/api/files/uploads").content(json.writeValueAsString(Map.of(
                "purpose", "TEAM_LOGO", "filename", "x.gif", "contentType", "image/gif", "size", 100))), 400);
    }
}
