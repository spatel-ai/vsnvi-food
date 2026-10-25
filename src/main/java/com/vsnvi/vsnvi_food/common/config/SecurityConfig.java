package com.vsnvi.vsnvi_food.common.config;

import com.vsnvi.vsnvi_food.common.security.BearerTokenAuthenticationFilter;
import com.vsnvi.vsnvi_food.common.security.ProblemDetailSecurityHandler;
import com.vsnvi.vsnvi_food.common.security.TokenAuthenticator;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless bearer-token security for the whole API (ADR-006). Default deny: only the exact paths in
 * {@code docs/SECURITY.md} §2 are public. Path variables in public patterns match UUIDs only, so e.g. the owner-only
 * {@code GET /restaurants/me} never matches the public {@code GET /restaurants/{id}}.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    static final String UUID = "{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}";

    static final String[] PUBLIC_POST = {
        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/payments/webhooks/razorpay"
    };

    static final String[] PUBLIC_GET = {
        "/api/v1/restaurants",
        "/api/v1/restaurants/" + UUID,
        "/api/v1/restaurants/" + UUID + "/menu",
        "/api/v1/menu-items/" + UUID,
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/health/readiness"
    };

    static final String[] API_DOCS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            ProblemDetailSecurityHandler problemHandler,
            ObjectProvider<TokenAuthenticator> tokenAuthenticator) {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        e -> e.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler))
                .addFilterBefore(
                        new BearerTokenAuthenticationFilter(tokenAuthenticator, problemHandler, credentialEndpoints()),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET)
                        .permitAll()
                        .anyRequest()
                        .authenticated());
        return http.build();
    }

    /** Endpoints that authenticate by credentials in the body, never by the bearer header. */
    static RequestMatcher credentialEndpoints() {
        var paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, "/api/v1/auth/register"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/login"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/refresh"));
    }

    /** Swagger UI and the spec are public outside prod only (docs/SECURITY.md §6). */
    @Bean
    @Order(1)
    @Profile("!prod")
    SecurityFilterChain apiDocsSecurity(HttpSecurity http) {
        http.securityMatcher(API_DOCS)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
