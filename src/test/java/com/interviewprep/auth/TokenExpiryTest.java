package com.interviewprep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.interviewprep.common.security.JwtProperties;
import com.interviewprep.common.security.SecurityConfig;
import com.interviewprep.user.AppUser;
import com.interviewprep.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Proves the 15-minute lifetime precisely: a token is issued at T and validated with clocks set to later instants.
 */
class TokenExpiryTest {

    private static final Instant ISSUED_AT = Instant.parse("2030-01-15T10:00:00Z");
    private static final JwtProperties PROPERTIES = new JwtProperties(
            "dGVzdC1vbmx5LWp3dC1zZWNyZXQtbmV2ZXItdXNlLW91dHNpZGUtdGVzdHMh", Duration.ofMinutes(15));

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void tokenIsAcceptedUpToAndIncludingFifteenMinutes() {
        String token = issueTokenAt(ISSUED_AT);

        assertThat(decodeAt(token, ISSUED_AT.plusSeconds(14 * 60 + 59))).isTrue();
        assertThat(decodeAt(token, ISSUED_AT.plusSeconds(15 * 60))).isTrue();
    }

    @Test
    void tokenIsRejectedOneSecondAfterFifteenMinutes() {
        String token = issueTokenAt(ISSUED_AT);

        assertThatThrownBy(() -> decodeAt(token, ISSUED_AT.plusSeconds(15 * 60 + 1)))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void secretShorterThan256BitsIsRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtProperties("dG9vLXNob3J0", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 256 bits");
    }

    private String issueTokenAt(Instant now) {
        AppUser user = new AppUser("user@example.com", "hash", Role.USER, now);
        ReflectionTestUtils.setField(user, "id", 1L);
        JwtTokenService tokenService = new JwtTokenService(
                securityConfig.jwtEncoder(PROPERTIES), PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));
        return tokenService.issueToken(user).accessToken();
    }

    private boolean decodeAt(String token, Instant now) {
        securityConfig.jwtDecoder(PROPERTIES, Clock.fixed(now, ZoneOffset.UTC)).decode(token);
        return true;
    }
}
