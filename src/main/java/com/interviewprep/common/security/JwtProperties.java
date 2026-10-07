package com.interviewprep.common.security;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings bound from {@code app.jwt.*}. The secret comes from the {@code JWT_SECRET} environment variable;
 * the application fails to start if it is missing or shorter than 256 bits.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration expiry) {

    private static final int MIN_KEY_BYTES = 32;

    public JwtProperties {
        if (secret != null && !secret.isBlank() && Base64.getDecoder().decode(secret).length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("app.jwt.secret must be a Base64-encoded key of at least 256 bits");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }
}
