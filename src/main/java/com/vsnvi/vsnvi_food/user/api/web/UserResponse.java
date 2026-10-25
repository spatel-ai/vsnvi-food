package com.vsnvi.vsnvi_food.user.api.web;

import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import com.vsnvi.vsnvi_food.user.api.UserSummary;
import java.util.UUID;

/** Admin view of a user. */
record UserResponse(UUID id, String email, String fullName, Role role, UserStatus status) {

    static UserResponse from(UserSummary user) {
        return new UserResponse(user.id(), user.email(), user.fullName(), user.role(), user.status());
    }
}
