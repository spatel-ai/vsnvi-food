package com.vsnvi.vsnvi_food.auth.api.web;

import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserSummary;
import java.util.UUID;

record RegisteredUserResponse(UUID id, String email, String fullName, Role role) {

    static RegisteredUserResponse from(UserSummary user) {
        return new RegisteredUserResponse(user.id(), user.email(), user.fullName(), user.role());
    }
}
