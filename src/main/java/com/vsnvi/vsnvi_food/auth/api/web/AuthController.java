package com.vsnvi.vsnvi_food.auth.api.web;

import com.vsnvi.vsnvi_food.auth.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "auth", description = "Registration, login and token refresh")
class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Register a customer, restaurant owner or rider", operationId = "register")
    ResponseEntity<RegisteredUserResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        var user = authService.register(
                request.email(),
                request.password(),
                request.fullName(),
                request.phone(),
                request.role().toRole(),
                http.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/users/me")).body(RegisteredUserResponse.from(user));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in and get access and refresh tokens", operationId = "login")
    TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // The client address as the servlet container sees it; behind a proxy, configure forward-headers-strategy.
        return TokenResponse.from(authService.login(request.email(), request.password(), http.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Rotate the refresh token and get a new access token", operationId = "refreshTokens")
    TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        return TokenResponse.from(authService.refresh(request.refreshToken(), http.getRemoteAddr()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and its rotations", operationId = "logout")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
