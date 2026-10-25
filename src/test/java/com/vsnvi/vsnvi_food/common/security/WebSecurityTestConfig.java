package com.vsnvi.vsnvi_food.common.security;

import com.vsnvi.vsnvi_food.common.config.SecurityConfig;
import com.vsnvi.vsnvi_food.common.web.ProblemDetailFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Real security filter chain for {@code @WebMvcTest}s, with a fake {@link TokenAuthenticator}: a token is
 * {@code <ROLE>:<userId>}, {@code expired} or anything else (invalid). Build headers with {@link #bearer}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfig.class, ProblemDetailSecurityHandler.class, ProblemDetailFactory.class, CurrentUser.class})
public class WebSecurityTestConfig {

    public static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    public static String bearer(String role, UUID userId) {
        return "Bearer " + role + ":" + userId;
    }

    @Bean
    Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    TokenAuthenticator fakeTokenAuthenticator() {
        return token -> {
            if (token.equals("expired")) {
                throw InvalidTokenException.expired();
            }
            var parts = token.split(":", 2);
            if (parts.length != 2) {
                throw InvalidTokenException.invalid();
            }
            try {
                return new AuthenticatedUser(UUID.fromString(parts[1]), parts[0], NOW.plusSeconds(900));
            } catch (IllegalArgumentException ex) {
                throw InvalidTokenException.invalid();
            }
        };
    }
}
