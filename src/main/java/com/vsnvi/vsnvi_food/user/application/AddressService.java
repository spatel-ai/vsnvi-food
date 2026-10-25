package com.vsnvi.vsnvi_food.user.application;

import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.CurrentUser;
import com.vsnvi.vsnvi_food.user.api.AddressSummary;
import com.vsnvi.vsnvi_food.user.domain.Address;
import com.vsnvi.vsnvi_food.user.domain.AddressLimitReachedException;
import com.vsnvi.vsnvi_food.user.domain.AddressRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's own addresses. Another user's address is reported as not found (404). */
@Service
@RequiredArgsConstructor
public class AddressService {

    static final int MAX_ADDRESSES = 20;

    private final AddressRepository addresses;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<AddressSummary> list() {
        return addresses.findAllByUserIdOrderByCreatedAtAsc(currentUser.id()).stream()
                .map(UserService::toSummary)
                .toList();
    }

    @Transactional
    public AddressSummary add(Address.Details details) {
        if (addresses.countByUserId(currentUser.id()) >= MAX_ADDRESSES) {
            throw new AddressLimitReachedException(MAX_ADDRESSES);
        }
        return UserService.toSummary(addresses.save(Address.create(currentUser.id(), details)));
    }

    @Transactional
    public AddressSummary replace(UUID addressId, Address.Details details) {
        var address = owned(addressId);
        address.replace(details);
        return UserService.toSummary(address);
    }

    @Transactional
    public void delete(UUID addressId) {
        addresses.delete(owned(addressId));
    }

    private Address owned(UUID addressId) {
        return addresses
                .findByIdAndUserId(addressId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Address " + addressId));
    }
}
