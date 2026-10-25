package com.vsnvi.vsnvi_food.user.api.web;

import com.vsnvi.vsnvi_food.user.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "user", description = "Profiles, addresses and admin user management")
class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get the caller's profile", operationId = "getMyProfile")
    ProfileResponse get() {
        return ProfileResponse.from(userService.currentProfile());
    }

    @PatchMapping
    @Operation(summary = "Update the caller's name or phone", operationId = "updateMyProfile")
    ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return ProfileResponse.from(userService.updateCurrentProfile(request.fullName(), request.phone()));
    }
}
