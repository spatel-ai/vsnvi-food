package com.vsnvi.vsnvi_food.common.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The authenticated caller. Application services use it for ownership checks, never an ID from the request body
 * (docs/SECURITY.md §2).
 */
@Component
public class CurrentUser {

    public UUID id() {
        return principal().id();
    }

    public String role() {
        return principal().role();
    }

    /** For public endpoints that behave differently for a signed-in caller. Empty when anonymous. */
    public Optional<AuthenticatedUser> ifAuthenticated() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    private AuthenticatedUser principal() {
        return ifAuthenticated()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No authenticated user"));
    }
}
