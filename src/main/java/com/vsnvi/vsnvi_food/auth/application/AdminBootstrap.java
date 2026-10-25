package com.vsnvi.vsnvi_food.auth.application;

import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first {@code ADMIN} from {@code auth.admin-bootstrap.email} and {@code .password}
 * ({@code ADMIN_BOOTSTRAP_*}), only if no admin exists yet (docs/SECURITY.md §2). Does nothing when they're unset.
 * The password is never logged.
 */
@Slf4j
@Component
class AdminBootstrap implements ApplicationRunner {

    private final UserApi userApi;
    private final AuthService authService;
    private final String email;
    private final String password;

    AdminBootstrap(
            UserApi userApi,
            AuthService authService,
            @Value("${auth.admin-bootstrap.email:}") String email,
            @Value("${auth.admin-bootstrap.password:}") String password) {
        this.userApi = userApi;
        this.authService = authService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        if (userApi.existsWithRole(Role.ADMIN)) {
            log.info(
                    "Admin bootstrap skipped: an admin already exists. Remove ADMIN_BOOTSTRAP_* from the environment.");
            return;
        }
        var admin = authService.createAccount(email, password, "Administrator", null, Role.ADMIN);
        log.info("Admin bootstrap created admin user {}", admin.id());
    }
}
