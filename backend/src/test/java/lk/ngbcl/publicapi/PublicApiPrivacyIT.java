package lk.ngbcl.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import lk.ngbcl.TestcontainersConfiguration;
import lk.ngbcl.files.InMemoryObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * M3 exit criterion: no private field appears in ANY public response. Seeds data that carries
 * private details (player contact + address + emergency contact, team contact), then calls every
 * GET endpoint under /api/public/** — discovered automatically, so new endpoints are covered too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PublicApiPrivacyIT {

    /** Keys that must never be serialised publicly (PUB-008 / NFR-014). */
    static final Set<String> FORBIDDEN_KEYS = Set.of("email", "phone", "address", "dateOfBirth", "gender",
            "emergencyName", "emergencyPhone", "contactName", "contactEmail", "contactPhone", "notes",
            "rejectionReason", "rejectionMessage", "ipHash", "authorId", "createdBy", "feeReceived",
            "statusReason", "registrationId", "previewUrl");

    /**
     * Intentional exceptions: the organiser's own contact details are public, and the registration
     * form describes WHICH fields it asks for (rule names, not anyone's data).
     */
    static final Map<String, Set<String>> ALLOWED = Map.of(
            "/api/public/tournament", Set.of("contactEmail", "contactPhone"),
            "/api/public/registration/player/form", Set.of("dateOfBirth", "gender", "address"));

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    InMemoryObjectStorage storage;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

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
            b.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
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

    @Test
    void noPublicEndpointLeaksPrivateFields() throws Exception {
        var login = mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Test-Admin-Password-1\"}")).andReturn();
        admin = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        openWindows();

        String n = Long.toString(ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L));
        String secretEmail = "private-" + n + "@example.com";
        String secretPhone = "+9477" + n.substring(0, 7);
        String secretAddress = "Secret Lane " + n;
        String teamEmail = "team-owner-" + n + "@example.com";

        // Approved player with every private field filled in
        String token = call(post("/api/public/registration/sessions"), null, null, Map.of("kind", "PLAYER"), 201)
                .get("token").asText();
        byte[] photo = png(600, 750);
        JsonNode ticket = call(post("/api/public/registration/uploads"), null, token,
                Map.of("contentType", "image/png", "size", photo.length), 201);
        storage.simulateUpload(ticket.get("uploadUrl").asText(), photo);
        String photoId = ticket.get("assetId").asText();
        call(post("/api/public/registration/uploads/" + photoId + "/complete"), null, token, null, 200);
        Map<String, Object> p = new HashMap<>();
        p.put("fullName", "Privacy Player " + n);
        p.put("email", secretEmail);
        p.put("phone", secretPhone);
        p.put("dateOfBirth", "1997-07-07");
        p.put("gender", "MALE");
        p.put("address", secretAddress);
        p.put("homeArea", "Nallur");
        p.put("experience", "Club player.");
        p.put("emergencyName", "Emergency Person " + n);
        p.put("emergencyPhone", "+9471" + n.substring(0, 7));
        p.put("photoAssetId", photoId);
        p.put("jaffnaDeclared", true);
        p.put("consent", true);
        call(post("/api/public/registration/player"), null, token, p, 201);
        String regId = call(get("/api/admin/registrations/players?status=PENDING&q=" + secretEmail), admin, null, null, 200)
                .at("/content/0/id").asText();
        call(post("/api/admin/registrations/players/" + regId + "/photo/verify"), admin, null, Map.of("approve", true), 200);
        call(post("/api/admin/registrations/players/" + regId + "/approve"), admin, null, Map.of(), 200);

        // Team with private contact details
        call(post("/api/admin/teams"), admin, null, Map.of("name", "Privacy Team " + n.substring(0, 5),
                "contactName", "Owner " + n, "contactEmail", teamEmail, "contactPhone", "+94770000" + n.substring(0, 3)), 201);

        // Content
        call(post("/api/admin/content/announcements"), admin, null, Map.of("title", "Privacy news " + n,
                "body", "Hello **Jaffna**.", "status", "PUBLISHED", "pinned", true), 201);
        call(post("/api/admin/content/sponsors"), admin, null, Map.of("name", "Sponsor " + n, "tier", "GOLD",
                "showOnWall", true, "showOnTicker", true, "active", true), 201);
        JsonNode groups = call(post("/api/admin/content/committee/groups"), admin, null,
                Map.of("name", "Leadership " + n, "style", "LEAD"), 201);
        String groupId = groups.get(groups.size() - 1).get("id").asText();
        call(post("/api/admin/content/committee/members"), admin, null, Map.of("groupId", groupId,
                "fullName", "Committee Person", "role", "Chair", "visible", true), 201);

        // Samples for path variables, taken from the public lists themselves
        Map<String, String> samples = new HashMap<>();
        samples.put("/api/public/players/{slug}", firstSlug("/api/public/players", "Privacy Player " + n));
        samples.put("/api/public/teams/{slug}", firstSlug("/api/public/teams", "Privacy Team " + n.substring(0, 5)));
        samples.put("/api/public/news/{slug}", call(get("/api/public/news"), null, null, null, 200)
                .at("/content/0/slug").asText());
        Map<String, String> queries = Map.of("/api/public/registration/team/name-available", "?name=Anything");

        Set<String> paths = new TreeSet<>();
        mappings.getHandlerMethods().forEach((info, method) -> {
            boolean isGet = info.getMethodsCondition().getMethods().isEmpty()
                    || info.getMethodsCondition().getMethods().contains(RequestMethod.GET);
            if (isGet) {
                info.getPatternValues().stream().filter(v -> v.startsWith("/api/public/")).forEach(paths::add);
            }
        });
        assertThat(paths).as("public GET endpoints discovered").hasSizeGreaterThanOrEqualTo(10);

        List<String> leaks = new ArrayList<>();
        for (String path : paths) {
            String url = path;
            if (path.contains("{")) {
                String sample = samples.get(path);
                if (sample == null) {
                    fail("No sample value for " + path + " — add one to PublicApiPrivacyIT so it is checked.");
                }
                Matcher m = Pattern.compile("\\{[^}]+}").matcher(path);
                url = m.replaceFirst(Matcher.quoteReplacement(sample));
            }
            url += queries.getOrDefault(path, "");
            var r = mvc.perform(get(url)).andReturn();
            String body = r.getResponse().getContentAsString();
            assertThat(r.getResponse().getStatus()).as(url + " → " + body).isEqualTo(200);
            for (String secret : List.of(secretEmail, secretPhone, secretAddress, teamEmail, "Emergency Person " + n)) {
                if (body.contains(secret)) {
                    leaks.add(url + " contains private value " + secret);
                }
            }
            List<String> found = new ArrayList<>();
            collectForbiddenKeys(json.readTree(body), url, "$", found);
            Set<String> allowed = ALLOWED.getOrDefault(path, Set.of());
            found.stream().filter(f -> allowed.stream().noneMatch(a -> f.endsWith("." + a))).forEach(leaks::add);
        }
        assertThat(leaks).as("private data in public responses").isEmpty();
    }

    private String firstSlug(String listUrl, String name) throws Exception {
        for (JsonNode item : call(get(listUrl), null, null, null, 200)) {
            if (name.equals(item.path("fullName").asText()) || name.equals(item.path("name").asText())) {
                return item.get("slug").asText();
            }
        }
        throw new AssertionError(name + " not found in " + listUrl);
    }

    private static void collectForbiddenKeys(JsonNode node, String url, String at, List<String> leaks) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                var e = it.next();
                if (FORBIDDEN_KEYS.contains(e.getKey())) {
                    leaks.add(url + " exposes " + at + "." + e.getKey());
                }
                collectForbiddenKeys(e.getValue(), url, at + "." + e.getKey(), leaks);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectForbiddenKeys(node.get(i), url, at + "[" + i + "]", leaks);
            }
        }
    }

    private void openWindows() throws Exception {
        JsonNode s = call(get("/api/admin/settings"), admin, null, null, 200);
        if ("DRAFT".equals(s.at("/general/status").asText())) {
            call(put("/api/admin/tournament/status"), admin, null, Map.of("status", "UPCOMING"), 200);
            s = call(get("/api/admin/settings"), admin, null, null, 200);
        }
        ObjectNode reg = (ObjectNode) s.get("registration").deepCopy();
        Instant now = Instant.now();
        reg.put("playerOpensAt", now.minus(1, ChronoUnit.DAYS).toString());
        reg.put("playerClosesAt", now.plus(10, ChronoUnit.DAYS).toString());
        reg.put("teamOpensAt", now.minus(1, ChronoUnit.DAYS).toString());
        reg.put("teamClosesAt", now.plus(10, ChronoUnit.DAYS).toString());
        reg.put("categoryRequired", false);
        reg.put("settingsVersion", s.get("settingsVersion").asLong());
        call(put("/api/admin/settings/registration"), admin, null, reg, 200);
    }
}
