package com.vsnvi.vsnvi_food.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates {@code Authorization: Bearer <token>} through the {@link TokenAuthenticator} SPI and stores the
 * {@link AuthenticatedUser} with its {@code ROLE_} authority. A request without the header continues anonymously; a
 * rejected token ends the request with 401 ({@code TOKEN_EXPIRED} or {@code UNAUTHORIZED}).
 *
 * <p>Not a {@code @Component}: it is added to the security filter chain only, never to the servlet filter chain.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final ObjectProvider<TokenAuthenticator> tokenAuthenticator;
    private final AuthenticationEntryPoint entryPoint;
    private final RequestMatcher ignored;

    /**
     * @param ignored requests whose bearer header is never read, e.g. {@code POST /auth/refresh}: a client that still
     *     sends its expired access token there must be able to refresh
     */
    public BearerTokenAuthenticationFilter(
            ObjectProvider<TokenAuthenticator> tokenAuthenticator,
            AuthenticationEntryPoint entryPoint,
            RequestMatcher ignored) {
        this.tokenAuthenticator = tokenAuthenticator;
        this.entryPoint = entryPoint;
        this.ignored = ignored;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return ignored.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            var user = authenticate(header.substring(PREFIX.length()).trim());
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (InvalidTokenException ex) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, ex);
            return;
        }
        chain.doFilter(request, response);
    }

    private AuthenticatedUser authenticate(String token) {
        var authenticator = tokenAuthenticator.getIfAvailable();
        if (authenticator == null || token.isEmpty()) {
            throw InvalidTokenException.invalid();
        }
        return authenticator.authenticate(token);
    }
}
