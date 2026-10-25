package com.vsnvi.vsnvi_food.user.api.web;

import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import com.vsnvi.vsnvi_food.user.api.UserSummary;
import java.util.UUID;

record ProfileResponse(UUID id, String email, String fullName, String phone, Role role, UserStatus status) {

    static ProfileResponse from(UserSummary user) {
        return new ProfileResponse(user.id(), user.email(), user.fullName(), user.phone(), user.role(), user.status());
    }
}
