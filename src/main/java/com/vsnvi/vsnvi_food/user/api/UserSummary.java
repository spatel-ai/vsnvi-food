package com.vsnvi.vsnvi_food.user.api;

import java.util.UUID;

/** Facade view of a user. {@code phone} may be null. */
public record UserSummary(UUID id, String email, String fullName, String phone, Role role, UserStatus status) {

    public boolean isBlocked() {
        return status == UserStatus.BLOCKED;
    }
}
