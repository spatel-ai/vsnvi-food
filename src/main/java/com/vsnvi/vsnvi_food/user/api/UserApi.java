package com.vsnvi.vsnvi_food.user.api;

import java.util.Optional;
import java.util.UUID;

/** The {@code user} module's facade for modules above it in the graph. */
public interface UserApi {

    /**
     * Creates an {@code ACTIVE} user. Registration ({@code auth}) never passes {@link Role#ADMIN}; only the admin
     * bootstrap does.
     *
     * @throws com.vsnvi.vsnvi_food.common.domain.DomainException with {@code EMAIL_ALREADY_REGISTERED} if the email
     *     is taken (case-insensitive)
     */
    UserSummary createUser(NewUser user);

    /** Case-insensitive. */
    Optional<UserSummary> findByEmail(String email);

    Optional<UserSummary> findById(UUID userId);

    /** Empty if the address doesn't exist or belongs to another user. */
    Optional<AddressSummary> findAddress(UUID userId, UUID addressId);

    boolean existsWithRole(Role role);
}
