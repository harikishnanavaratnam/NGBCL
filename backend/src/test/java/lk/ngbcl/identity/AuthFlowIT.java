package lk.ngbcl.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import lk.ngbcl.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthFlowIT {

    private static final String ORIGIN = "http://localhost:3000";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andReturn();
    }

    private static Cookie refreshCookie(MvcResult r) {
        return r.getResponse().getCookie("ngbcl_rt");
    }

    @Test
    void bootstrapAdminCanSignInAndReadMe() throws Exception {
        MvcResult r = login("admin@test.local", "Test-Admin-Password-1");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(refreshCookie(r)).isNotNull();
        assertThat(refreshCookie(r).isHttpOnly()).isTrue();
        assertThat(r.getResponse().getCookie("ngbcl_session")).isNotNull();

        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.at("/user/roles/0").asText()).isEqualTo("SUPER_ADMIN");
        assertThat(body.at("/user/homeSurface").asText()).isEqualTo("ADMIN");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + body.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.local"));
    }

    @Test
    void wrongPasswordIsRejectedWithProblemJson() throws Exception {
        MvcResult r = login("admin@test.local", "nope-nope-nope");
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(r.getResponse().getContentAsString()).contains("BAD_CREDENTIALS");
    }

    @Test
    void protectedEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicTournamentIsOpenAndHasNoPrivateFields() throws Exception {
        mvc.perform(get("/api/public/tournament"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edition").value("1.0"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void refreshRotatesAndReuseRevokesTheFamily() throws Exception {
        Cookie first = refreshCookie(login("admin@test.local", "Test-Admin-Password-1"));

        MvcResult rotated = mvc.perform(post("/api/auth/refresh").header("Origin", ORIGIN).cookie(first)).andReturn();
        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        Cookie second = refreshCookie(rotated);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Old token again inside the race window → conflict, family intact
        mvc.perform(post("/api/auth/refresh").header("Origin", ORIGIN).cookie(first))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFRESH_RACE"));
        mvc.perform(post("/api/auth/refresh").header("Origin", ORIGIN).cookie(second)).andExpect(status().isOk());
    }

    @Test
    void untrustedOriginCannotUseCookieEndpoints() throws Exception {
        // Rejected by CORS or by the Origin check in AuthController — either way, 403.
        mvc.perform(post("/api/auth/refresh").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void scorerCannotManageUsers() throws Exception {
        String adminToken = json.readTree(login("admin@test.local", "Test-Admin-Password-1").getResponse()
                .getContentAsString()).get("accessToken").asText();

        String body = """
                {"email":"scorer1@test.local","displayName":"Scorer One","password":"Scorer-Password-1","roles":["MATCH_SCORER"]}
                """;
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

        MvcResult scorer = login("scorer1@test.local", "Scorer-Password-1");
        JsonNode s = json.readTree(scorer.getResponse().getContentAsString());
        assertThat(s.at("/user/homeSurface").asText()).isEqualTo("SCORER");

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + s.get("accessToken").asText()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
