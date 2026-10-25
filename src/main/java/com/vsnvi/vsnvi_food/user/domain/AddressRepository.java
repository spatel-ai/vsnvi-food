package com.vsnvi.vsnvi_food.user.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository {

    Address save(Address address);

    void delete(Address address);

    List<Address> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    /** The ownership check: empty for another user's address. */
    Optional<Address> findByIdAndUserId(UUID id, UUID userId);
}
