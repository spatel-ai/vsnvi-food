package com.vsnvi.vsnvi_food.user.api.web;

import com.vsnvi.vsnvi_food.common.web.PageResponse;
import com.vsnvi.vsnvi_food.common.web.Pageables;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import com.vsnvi.vsnvi_food.user.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "user", description = "Profiles, addresses and admin user management")
class UserAdminController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "email", "fullName");

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List users, filtered by role and status", operationId = "listUsers")
    PageResponse<UserResponse> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = userService.search(role, status, Pageables.requireSortableBy(pageable, SORTABLE));
        return PageResponse.from(page, UserResponse::from);
    }

    @PostMapping("/{id}/block")
    @Operation(summary = "Block a user and revoke their sessions", operationId = "blockUser")
    UserResponse block(@PathVariable UUID id, @Valid @RequestBody BlockUserRequest request) {
        return UserResponse.from(userService.block(id, request.reason()));
    }
}
