package com.vsnvi.vsnvi_food.user.domain;

import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository {

    User save(User user);

    /** Flushes, so a duplicate email fails here rather than at commit. */
    User saveAndFlush(User user);

    Optional<User> findById(UUID id);

    /** {@code email} must already be normalized. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    /** Null filters match everything. */
    Page<User> search(Role role, UserStatus status, Pageable pageable);
}
