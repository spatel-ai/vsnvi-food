package com.vsnvi.vsnvi_food.auth.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code auth.jwt.*}. {@code signingKey} is base64 and at least 256 bits (from {@code JWT_SIGNING_KEY}); it is never
 * logged. {@code toString} is overridden so it can't leak through property dumps.
 */
@Validated
@ConfigurationProperties("auth.jwt")
record JwtProperties(
        @NotBlank String signingKey,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + ", refreshTokenTtl="
                + refreshTokenTtl + ", signingKey=****]";
    }
}
