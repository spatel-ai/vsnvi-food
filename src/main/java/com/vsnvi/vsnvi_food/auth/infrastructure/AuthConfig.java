package com.vsnvi.vsnvi_food.auth.infrastructure;

import java.time.Clock;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class AuthConfig {

    static final int BCRYPT_STRENGTH = 12;

    /** One bean for both {@code AccessTokenIssuer} and the {@code TokenAuthenticator} SPI. */
    @Bean
    JwtService jwtService(JwtProperties properties, Clock clock) {
        return new JwtService(properties, clock);
    }

    /** BCrypt(12) through the delegating encoder, so hashes are prefixed ({bcrypt}) and can be upgraded later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(BCRYPT_STRENGTH)));
    }
}
