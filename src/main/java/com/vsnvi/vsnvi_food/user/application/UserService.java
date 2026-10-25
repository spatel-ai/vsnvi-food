package com.vsnvi.vsnvi_food.user.application;

import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.CurrentUser;
import com.vsnvi.vsnvi_food.user.api.AddressSummary;
import com.vsnvi.vsnvi_food.user.api.NewUser;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserApi;
import com.vsnvi.vsnvi_food.user.api.UserBlocked;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import com.vsnvi.vsnvi_food.user.api.UserSummary;
import com.vsnvi.vsnvi_food.user.domain.Address;
import com.vsnvi.vsnvi_food.user.domain.AddressRepository;
import com.vsnvi.vsnvi_food.user.domain.CannotBlockAdminException;
import com.vsnvi.vsnvi_food.user.domain.EmailAlreadyRegisteredException;
import com.vsnvi.vsnvi_food.user.domain.User;
import com.vsnvi.vsnvi_food.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserApi {

    private final UserRepository users;
    private final AddressRepository addresses;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional
    public UserSummary createUser(NewUser newUser) {
        var email = User.normalizeEmail(newUser.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            var user = users.saveAndFlush(User.register(email, newUser.fullName(), newUser.phone(), newUser.role()));
            log.info("Created user {} with role {}", user.getId(), user.getRole());
            return toSummary(user);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent registration won the unique constraint.
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return users.findByEmail(User.normalizeEmail(email)).map(UserService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findById(UUID userId) {
        return users.findById(userId).map(UserService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AddressSummary> findAddress(UUID userId, UUID addressId) {
        return addresses.findByIdAndUserId(addressId, userId).map(UserService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsWithRole(Role role) {
        return users.existsByRole(role);
    }

    @Transactional(readOnly = true)
    public UserSummary currentProfile() {
        return users.findById(currentUser.id())
                .map(UserService::toSummary)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public UserSummary updateCurrentProfile(String fullName, String phone) {
        var user = users.findById(currentUser.id()).orElseThrow(() -> new NotFoundException("User not found"));
        user.updateProfile(fullName, phone);
        return toSummary(user);
    }

    @Transactional(readOnly = true)
    public Page<UserSummary> search(Role role, UserStatus status, Pageable pageable) {
        return users.search(role, status, pageable).map(UserService::toSummary);
    }

    /** Idempotent: blocking a blocked user changes nothing and publishes nothing. */
    @Transactional
    public UserSummary block(UUID userId, String reason) {
        var user = users.findById(userId).orElseThrow(() -> new NotFoundException("User " + userId));
        if (user.getRole() == Role.ADMIN) {
            // Covers blocking yourself and the last admin; there is no unblock endpoint to recover from either.
            throw new CannotBlockAdminException();
        }
        if (user.block()) {
            events.publishEvent(new UserBlocked(user.getId(), reason, Instant.now(clock)));
            log.info("Blocked user {} (by admin {})", user.getId(), currentUser.id());
        }
        return toSummary(user);
    }

    static UserSummary toSummary(User user) {
        return new UserSummary(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getRole(), user.getStatus());
    }

    static AddressSummary toSummary(Address a) {
        return new AddressSummary(
                a.getId(),
                a.getUserId(),
                a.getLabel(),
                a.getLine1(),
                a.getLine2(),
                a.getCity(),
                a.getState(),
                a.getPostalCode(),
                a.getLatitude(),
                a.getLongitude());
    }
}
