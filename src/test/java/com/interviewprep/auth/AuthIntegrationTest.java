package com.interviewprep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.interviewprep.user.AppUser;
import com.interviewprep.user.Role;
import com.interviewprep.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end security tests through the real filter chain, with a fixed clock so token times are deterministic.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-15T10:00:00Z");
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String USER_EMAIL = "user@example.com";
    private static final String PASSWORD = "correct-horse-battery";

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(new AppUser(ADMIN_EMAIL, passwordEncoder.encode(PASSWORD), Role.ADMIN, NOW));
    }

    // --- Authorization: USER vs ADMIN -------------------------------------------------------------------------

    @Test
    void userCannotListAllUsers() throws Exception {
        String userToken = registerAndLogin(USER_EMAIL);

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.instance").value("/api/users"));
    }

    @Test
    void adminCanListAllUsersWithoutPasswordHashes() throws Exception {
        registerAndLogin(USER_EMAIL);
        String adminToken = login(ADMIN_EMAIL);

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].email").value(USER_EMAIL))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void anyAuthenticatedUserCanViewOwnProfile() throws Exception {
        String userToken = registerAndLogin(USER_EMAIL);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(USER_EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").value(NOW.toString()));
    }

    // --- 401 vs 403 -------------------------------------------------------------------------------------------

    @Test
    void requestWithoutTokenReturns401Json() throws Exception {
        for (String path : List.of("/api/users/me", "/api/users")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource"));
        }
    }

    @Test
    void tamperedTokenReturns401() throws Exception {
        String token = registerAndLogin(USER_EMAIL);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Access token is invalid or expired"));
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String expired = signedToken(NOW.minus(20, ChronoUnit.MINUTES), NOW.minus(5, ChronoUnit.MINUTES));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Access token is invalid or expired"));
    }

    @Test
    void tokenSignedWithAnotherKeyReturns401() throws Exception {
        String foreignToken = "eyJhbGciOiJIUzI1NiJ9."
                + "eyJzdWIiOiIxIiwicm9sZXMiOlsiQURNSU4iXSwiZXhwIjo0MTAyNDQ0ODAwfQ."
                + "c2lnbmVkLXdpdGgtYS1kaWZmZXJlbnQta2V5LXh4eHh4eHh4eHh4eHh4eHg";

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isUnauthorized());
    }

    // --- Register / login -------------------------------------------------------------------------------------

    @Test
    void loginReturnsBearerTokenValidFor15Minutes() throws Exception {
        register(USER_EMAIL);
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(USER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        AppUser user = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(15, ChronoUnit.MINUTES));
    }

    @Test
    void registrationAlwaysCreatesUserRoleEvenIfAdminRequested() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "role": "ADMIN"}
                                """.formatted(USER_EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        register(USER_EMAIL);

        String hash = userRepository.findByEmail(USER_EMAIL).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        register(USER_EMAIL);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("User@Example.COM", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        register(USER_EMAIL);

        for (String[] attempt : List.of(
                new String[] {USER_EMAIL, "wrong-password"},
                new String[] {"nobody@example.com", PASSWORD})) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(attempt[0], attempt[1])))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }
    }

    @Test
    void registerValidatesInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void passwordOverBcryptByteLimitIsRejected() throws Exception {
        String seventyTwoTwoByteChars = "é".repeat(72);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(USER_EMAIL, seventyTwoTwoByteChars)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(USER_EMAIL, seventyTwoTwoByteChars)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void earlierQuestionEndpointsRemainPublic() throws Exception {
        mockMvc.perform(get("/api/tasks")).andExpect(status().isOk());
    }

    // --- helpers ----------------------------------------------------------------------------------------------

    private String registerAndLogin(String email) throws Exception {
        register(email);
        return login(email);
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String signedToken(Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("ADMIN"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
