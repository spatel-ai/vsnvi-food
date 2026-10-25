package com.vsnvi.vsnvi_food.common.security;

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

    private AuthenticatedUser principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }
}
