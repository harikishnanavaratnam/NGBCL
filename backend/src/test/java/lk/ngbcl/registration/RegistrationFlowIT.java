package lk.ngbcl.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
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

/** M2: public registration → review → approval, duplicates, teams and privacy rules. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RegistrationFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    InMemoryObjectStorage storage;

    String admin;

    // ---- Helpers ---------------------------------------------------------------------------

    @BeforeEach
    void signInAndOpenWindows() throws Exception {
        var r = mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Test-Admin-Password-1\"}")).andReturn();
        admin = json.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
        setWindows(true);
    }

    void setWindows(boolean open) throws Exception {
        JsonNode s = call(get("/api/admin/settings"), admin, null, 200);
        if ("DRAFT".equals(s.at("/general/status").asText())) {
            call(put("/api/admin/tournament/status"), admin, Map.of("status", "UPCOMING"), 200);
            s = call(get("/api/admin/settings"), admin, null, 200);
        }
        ObjectNode reg = (ObjectNode) s.get("registration").deepCopy();
        Instant now = Instant.now();
        Instant opens = open ? now.minus(1, ChronoUnit.DAYS) : now.minus(10, ChronoUnit.DAYS);
        Instant closes = open ? now.plus(10, ChronoUnit.DAYS) : now.minus(1, ChronoUnit.DAYS);
        reg.put("playerOpensAt", opens.toString());
        reg.put("playerClosesAt", closes.toString());
        reg.put("teamOpensAt", opens.toString());
        reg.put("teamClosesAt", closes.toString());
        reg.put("categoryRequired", false);
        reg.put("settingsVersion", s.get("settingsVersion").asLong());
        call(put("/api/admin/settings/registration"), admin, reg, 200);
    }

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
            b.contentType(MediaType.APPLICATION_JSON)
                    .content(body instanceof String str ? str : json.writeValueAsString(body));
        }
        var r = mvc.perform(b).andReturn();
        String s = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).as(s).isEqualTo(expected);
        return s.isEmpty() ? null : json.readTree(s);
    }

    static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    String session(String kind) throws Exception {
        return call(post("/api/public/registration/sessions"), null, Map.of("kind", kind), 201).get("token").asText();
    }

    String upload(String token, byte[] bytes) throws Exception {
        JsonNode ticket = call(post("/api/public/registration/uploads"), null, token,
                Map.of("filename", "me.png", "contentType", "image/png", "size", bytes.length), 201);
        storage.simulateUpload(ticket.get("uploadUrl").asText(), bytes);
        String id = ticket.get("assetId").asText();
        call(post("/api/public/registration/uploads/" + id + "/complete"), null, token, null, 200);
        return id;
    }

    static String rand() {
        return Long.toString(ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L));
    }

    Map<String, Object> player(String name, String email, String phone, String photoId) {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", name);
        m.put("email", email);
        m.put("phone", phone);
        m.put("dateOfBirth", "1998-04-12");
        m.put("gender", "MALE");
        m.put("homeArea", "Nallur");
        m.put("photoAssetId", photoId);
        m.put("jaffnaDeclared", true);
        m.put("consent", true);
        return m;
    }

    String submitPlayer(String name, String email, String phone) throws Exception {
        String token = session("PLAYER");
        String photo = upload(token, png(600, 750));
        JsonNode receipt = call(post("/api/public/registration/player"), null, token, player(name, email, phone, photo), 201);
        assertThat(receipt.get("referenceNumber").asText()).matches("PLY-\\d{4}-\\d{5}");
        JsonNode page = call(get("/api/admin/registrations/players?status=PENDING&q=" + email), admin, null, 200);
        assertThat(page.get("content")).hasSize(1);
        return page.at("/content/0/id").asText();
    }

    JsonNode verifyAndApprove(String regId) throws Exception {
        call(post("/api/admin/registrations/players/" + regId + "/photo/verify"), admin, Map.of("approve", true), 200);
        return call(post("/api/admin/registrations/players/" + regId + "/approve"), admin, Map.of(), 200);
    }

    // ---- Tests -----------------------------------------------------------------------------

    @Test
    void playerRegistersThenIsReviewedAndApproved() throws Exception {
        JsonNode form = call(get("/api/public/registration/player/form"), null, null, 200);
        assertThat(form.at("/window/state").asText()).isEqualTo("OPEN");
        assertThat(form.at("/photo/ratioW").asInt()).isEqualTo(4);

        String n = rand();
        String email = "kasun" + n + "@example.com";
        String regId = submitPlayer("Kasun Approve " + n, email, "077" + n.substring(0, 7));

        JsonNode detail = call(get("/api/admin/registrations/players/" + regId), admin, null, 200);
        assertThat(detail.get("status").asText()).isEqualTo("PENDING");
        assertThat(detail.at("/photo/previewUrl").asText()).isNotBlank();
        assertThat(detail.at("/blockers/0/code").asText()).isEqualTo("PHOTO_NOT_VERIFIED");

        JsonNode err = call(post("/api/admin/registrations/players/" + regId + "/approve"), admin, Map.of(), 422);
        assertThat(err.get("code").asText()).isEqualTo("PHOTO_NOT_VERIFIED");

        call(post("/api/admin/registrations/players/" + regId + "/notes"), admin, Map.of("body", "Called — confirmed."), 200);
        JsonNode approved = verifyAndApprove(regId);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("playerId").asText()).isNotBlank();
        assertThat(approved.at("/notes/0/body").asText()).isEqualTo("Called — confirmed.");
        assertThat(storage.publicObjects).isNotEmpty();

        // Approving twice is refused
        call(post("/api/admin/registrations/players/" + regId + "/approve"), admin, Map.of(), 409);

        JsonNode dir = call(get("/api/admin/players?q=" + email), admin, null, 200);
        assertThat(dir.at("/content/0/status").asText()).isEqualTo("APPROVED");

        // Public directory: listed, but never with contact details (PUB-008)
        var pub = mvc.perform(get("/api/public/players")).andReturn().getResponse().getContentAsString();
        assertThat(pub).contains("Kasun Approve " + n).doesNotContain(email).doesNotContain("\"email\"")
                .doesNotContain("\"phone\"");
    }

    @Test
    void sessionIsSingleUseAndScopedToItsOwnUploads() throws Exception {
        String n = rand();
        String token = session("PLAYER");
        String photo = upload(token, png(600, 750));
        call(post("/api/public/registration/player"), null, token,
                player("Single Use " + n, "single" + n + "@example.com", "071" + n.substring(0, 7), photo), 201);
        // Same token again → expired
        JsonNode again = call(post("/api/public/registration/player"), null, token,
                player("Single Use " + n, "single" + n + "@example.com", "071" + n.substring(0, 7), photo), 410);
        assertThat(again.get("code").asText()).isEqualTo("REGISTRATION_SESSION_EXPIRED");

        // Another session can't use that photo
        String other = session("PLAYER");
        JsonNode stolen = call(post("/api/public/registration/player"), null, other,
                player("Thief " + n, "thief" + n + "@example.com", "072" + n.substring(0, 7), photo), 404);
        assertThat(stolen.get("code").asText()).isEqualTo("NOT_FOUND");

        // No token at all
        call(post("/api/public/registration/uploads"), null, null,
                Map.of("contentType", "image/png", "size", 100), 410);
    }

    @Test
    void submissionRulesAreEnforcedOnTheServer() throws Exception {
        String token = session("PLAYER");
        String photo = upload(token, png(600, 750));
        Map<String, Object> bad = player(" ", "not-an-email", "12", photo);
        bad.put("jaffnaDeclared", false);
        JsonNode err = call(post("/api/public/registration/player"), null, token, bad, 400);
        assertThat(err.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(err.get("errors").toString()).contains("fullName", "email", "phone", "jaffnaDeclared");

        // Wrong ratio photo is rejected at upload time
        JsonNode ticket = call(post("/api/public/registration/uploads"), null, token,
                Map.of("contentType", "image/png", "size", png(800, 800).length), 201);
        storage.simulateUpload(ticket.get("uploadUrl").asText(), png(800, 800));
        JsonNode ratio = call(post("/api/public/registration/uploads/" + ticket.get("assetId").asText() + "/complete"),
                null, token, null, 422);
        assertThat(ratio.get("code").asText()).isEqualTo("IMAGE_WRONG_RATIO");
    }

    @Test
    void closedWindowRefusesNewSessions() throws Exception {
        setWindows(false);
        try {
            JsonNode err = call(post("/api/public/registration/sessions"), null, Map.of("kind", "PLAYER"), 409);
            assertThat(err.get("code").asText()).isEqualTo("REGISTRATION_CLOSED");
            JsonNode form = call(get("/api/public/registration/team/form"), null, null, 200);
            assertThat(form.at("/window/state").asText()).isEqualTo("CLOSED");
        } finally {
            setWindows(true);
        }
    }

    @Test
    void duplicatesMustBeResolvedBeforeApproval() throws Exception {
        String n = rand();
        String email = "dup" + n + "@example.com";
        String first = submitPlayer("Dup Original " + n, email, "075" + n.substring(0, 7));
        verifyAndApprove(first);

        String second = submitPlayer("Dup Again " + n, email, "076" + n.substring(0, 7));
        JsonNode detail = call(get("/api/admin/registrations/players/" + second), admin, null, 200);
        assertThat(detail.get("duplicateFlag").asBoolean()).isTrue();
        JsonNode dup = detail.at("/duplicates/0");
        assertThat(dup.get("matchedOn").asText()).contains("email");
        assertThat(dup.at("/player/inThisEdition").asBoolean()).isTrue();

        call(post("/api/admin/registrations/players/" + second + "/photo/verify"), admin, Map.of("approve", true), 200);
        JsonNode blocked = call(post("/api/admin/registrations/players/" + second + "/approve"), admin, Map.of(), 422);
        assertThat(blocked.get("code").asText()).isEqualTo("DUPLICATE_UNRESOLVED");

        // Linking to a player already in this edition is refused; reviewer rejects instead
        JsonNode link = call(post("/api/admin/registrations/players/" + second + "/duplicates/" + dup.get("id").asText()
                + "/resolve"), admin, Map.of("action", "LINK_EXISTING"), 409);
        assertThat(link.get("code").asText()).isEqualTo("ALREADY_IN_TOURNAMENT");

        JsonNode noReason = call(post("/api/admin/registrations/players/" + second + "/reject"), admin, Map.of("reason", ""), 400);
        assertThat(noReason.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        JsonNode rejected = call(post("/api/admin/registrations/players/" + second + "/reject"), admin,
                Map.of("reason", "Duplicate registration", "message", "You're already registered."), 200);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
    }

    @Test
    void reviewerCanCorrectDetailsAndBulkApprove() throws Exception {
        String n = rand();
        String a = submitPlayer("Bulk One " + n, "bulk1" + n + "@example.com", "078" + n.substring(0, 7));
        String b = submitPlayer("Bulk Two " + n, "bulk2" + n + "@example.com", "079" + n.substring(0, 7));

        JsonNode d = call(get("/api/admin/registrations/players/" + a), admin, null, 200);
        Map<String, Object> edit = new HashMap<>();
        edit.put("fullName", "Bulk One Fixed " + n);
        edit.put("email", d.get("email").asText());
        edit.put("phone", d.get("phone").asText());
        edit.put("version", d.get("version").asLong());
        JsonNode edited = call(patch("/api/admin/registrations/players/" + a), admin, edit, 200);
        assertThat(edited.get("fullName").asText()).isEqualTo("Bulk One Fixed " + n);
        // Stale version
        call(patch("/api/admin/registrations/players/" + a), admin, edit, 409);

        call(post("/api/admin/registrations/players/" + a + "/photo/verify"), admin, Map.of("approve", true), 200);
        JsonNode result = call(post("/api/admin/registrations/players/bulk-approve"), admin,
                Map.of("ids", new String[] {a, b}), 200);
        assertThat(result.get("approved").toString()).contains(a);
        assertThat(result.at("/skipped/0/code").asText()).isEqualTo("PHOTO_NOT_VERIFIED");
    }

    @Test
    void teamApplicationBecomesATeamWithUniqueName() throws Exception {
        String n = rand();
        String name = "Valvai Smashers " + n.substring(0, 4);
        JsonNode avail = call(get("/api/public/registration/team/name-available?name=" + name), null, null, 200);
        assertThat(avail.get("available").asBoolean()).isTrue();

        String token = session("TEAM");
        String logo = upload(token, png(512, 512));
        Map<String, Object> team = new HashMap<>();
        team.put("teamName", name);
        team.put("logoAssetId", logo);
        team.put("contactName", "Team Owner");
        team.put("contactEmail", "owner" + n + "@example.com");
        team.put("contactPhone", "0770000000");
        team.put("authorised", true);
        team.put("feeAcknowledged", true);
        team.put("consent", true);
        JsonNode receipt = call(post("/api/public/registration/team"), null, token, team, 201);
        assertThat(receipt.get("referenceNumber").asText()).matches("TEM-\\d{4}-\\d{4}");

        // Name is now held by the pending application
        assertThat(call(get("/api/public/registration/team/name-available?name=" + name.toLowerCase()), null, null, 200)
                .get("available").asBoolean()).isFalse();

        JsonNode list = call(get("/api/admin/registrations/teams?status=PENDING&q=" + n.substring(0, 4)), admin, null, 200);
        String regId = list.at("/content/0/id").asText();
        JsonNode blocked = call(post("/api/admin/registrations/teams/" + regId + "/approve"), admin, Map.of(), 422);
        assertThat(blocked.get("code").asText()).isEqualTo("LOGO_NOT_VERIFIED");

        call(post("/api/admin/registrations/teams/" + regId + "/logo/verify"), admin, Map.of("approve", true), 200);
        JsonNode approved = call(post("/api/admin/registrations/teams/" + regId + "/approve"), admin, Map.of(), 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");

        JsonNode teams = call(get("/api/admin/teams"), admin, null, 200);
        JsonNode created = null;
        for (JsonNode t : teams) {
            if (t.get("name").asText().equals(name)) {
                created = t;
            }
        }
        assertThat(created).isNotNull();
        assertThat(created.get("shortCode").asText()).matches("[A-Z0-9]{2,3}");
        assertThat(created.get("creationSource").asText()).isEqualTo("PUBLIC_REGISTRATION");

        // Admin can't create a second team with the same name; fee can be marked received
        call(post("/api/admin/teams"), admin, Map.of("name", name.toUpperCase()), 409);
        JsonNode fee = call(post("/api/admin/teams/" + created.get("id").asText() + "/fee"), admin,
                Map.of("received", true, "version", created.get("version").asLong()), 200);
        assertThat(fee.get("feeReceived").asBoolean()).isTrue();

        var pub = mvc.perform(get("/api/public/teams")).andReturn().getResponse().getContentAsString();
        assertThat(pub).contains(name).doesNotContain("owner" + n);
    }

    @Test
    void staffEndpointsNeedSignIn() throws Exception {
        call(get("/api/admin/registrations/players"), null, null, 401);
        call(get("/api/admin/players"), null, null, 401);
        call(post("/api/admin/teams"), null, Map.of("name", "Nope"), 401);
    }
}
