package com.vsnvi.vsnvi_food.user.infrastructure;

import com.vsnvi.vsnvi_food.user.domain.Address;
import com.vsnvi.vsnvi_food.user.domain.AddressRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AddressJpaRepository extends JpaRepository<Address, UUID>, AddressRepository {}
