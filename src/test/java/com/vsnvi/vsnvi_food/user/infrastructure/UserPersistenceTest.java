package com.vsnvi.vsnvi_food.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.TestcontainersConfiguration;
import com.vsnvi.vsnvi_food.common.config.TimeConfig;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import com.vsnvi.vsnvi_food.user.domain.Address;
import com.vsnvi.vsnvi_food.user.domain.AddressRepository;
import com.vsnvi.vsnvi_food.user.domain.User;
import com.vsnvi.vsnvi_food.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

/** Mappings, the V2 migration, custom queries and Clock-based audit timestamps against real Postgres. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, TimeConfig.class, UserPersistenceTest.FixedClock.class})
class UserPersistenceTest {

    static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    private final UserRepository users;
    private final AddressRepository addresses;

    UserPersistenceTest(@Autowired UserRepository users, @Autowired AddressRepository addresses) {
        this.users = users;
        this.addresses = addresses;
    }

    @Test
    void should_set_audit_timestamps_from_clock_when_saving() {
        var user = users.saveAndFlush(User.register("a@example.com", "Asha", null, Role.CUSTOMER));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void should_reject_duplicate_email_when_saving() {
        users.saveAndFlush(User.register("dup@example.com", "Asha", null, Role.CUSTOMER));

        assertThatThrownBy(() -> users.saveAndFlush(User.register("DUP@example.com", "Ravi", null, Role.CUSTOMER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_filter_by_role_and_status_when_searching() {
        users.save(User.register("c1@example.com", "C1", null, Role.CUSTOMER));
        var blocked = User.register("c2@example.com", "C2", null, Role.CUSTOMER);
        blocked.block();
        users.save(blocked);
        users.saveAndFlush(User.register("o1@example.com", "O1", null, Role.RESTAURANT_OWNER));

        assertThat(users.search(Role.CUSTOMER, null, PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(2);
        assertThat(users.search(Role.CUSTOMER, UserStatus.BLOCKED, PageRequest.of(0, 10))
                        .getContent())
                .extracting(User::getEmail)
                .containsExactly("c2@example.com");
        assertThat(users.search(null, null, PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(3);
        assertThat(users.existsByRole(Role.ADMIN)).isFalse();
    }

    @Test
    void should_find_address_only_for_its_owner() {
        var owner = users.saveAndFlush(User.register("o@example.com", "Owner", null, Role.CUSTOMER));
        var address = addresses.save(Address.create(
                owner.getId(),
                new Address.Details("Home", "12 MG Road", null, "Bengaluru", "Karnataka", "560001", 12.97, 77.59)));

        assertThat(addresses.findByIdAndUserId(address.getId(), owner.getId())).isPresent();
        assertThat(addresses.findByIdAndUserId(address.getId(), UUID.randomUUID()))
                .isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
