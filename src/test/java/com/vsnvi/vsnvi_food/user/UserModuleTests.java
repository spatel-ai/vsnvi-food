package com.vsnvi.vsnvi_food.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.TestcontainersConfiguration;
import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.AuthenticatedUser;
import com.vsnvi.vsnvi_food.user.api.NewUser;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserApi;
import com.vsnvi.vsnvi_food.user.api.UserBlocked;
import com.vsnvi.vsnvi_food.user.application.AddressService;
import com.vsnvi.vsnvi_food.user.application.UserService;
import com.vsnvi.vsnvi_food.user.domain.Address;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ApplicationModuleTest(extraIncludes = "common")
@Import(TestcontainersConfiguration.class)
class UserModuleTests {

    private final UserApi userApi;
    private final UserService userService;
    private final AddressService addressService;

    UserModuleTests(
            @Autowired UserApi userApi, @Autowired UserService userService, @Autowired AddressService addressService) {
        this.userApi = userApi;
        this.userService = userService;
        this.addressService = addressService;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_find_created_user_by_email_ignoring_case_when_created_through_facade() {
        var created = userApi.createUser(new NewUser("Mixed.Case@Example.com", "Mixed", null, Role.CUSTOMER));

        assertThat(userApi.findByEmail("mixed.case@EXAMPLE.com"))
                .get()
                .extracting(u -> u.id())
                .isEqualTo(created.id());
        assertThat(userApi.findById(created.id())).isPresent();
    }

    @Test
    void should_return_email_already_registered_when_email_taken() {
        userApi.createUser(new NewUser("taken@example.com", "First", null, Role.CUSTOMER));

        assertThatThrownBy(() -> userApi.createUser(new NewUser("TAKEN@example.com", "Second", null, Role.RIDER)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void should_publish_UserBlocked_once_when_user_blocked_twice(AssertablePublishedEvents events) {
        var user = userApi.createUser(new NewUser("block-me@example.com", "Block", null, Role.CUSTOMER));
        signInAs(UUID.randomUUID(), "ADMIN");

        userService.block(user.id(), "fraud");
        userService.block(user.id(), "fraud again");

        assertThat(events.ofType(UserBlocked.class).matching(e -> e.userId().equals(user.id())))
                .hasSize(1);
        assertThat(userApi.findById(user.id()).orElseThrow().isBlocked()).isTrue();
    }

    @Test
    void should_hide_address_from_other_users_when_accessed_by_id() {
        var owner = userApi.createUser(new NewUser("owner@example.com", "Owner", null, Role.CUSTOMER));
        signInAs(owner.id(), "CUSTOMER");
        var address = addressService.add(
                new Address.Details("Home", "12 MG Road", null, "Bengaluru", "Karnataka", "560001", 12.97, 77.59));

        assertThat(userApi.findAddress(owner.id(), address.id())).isPresent();
        assertThat(userApi.findAddress(UUID.randomUUID(), address.id())).isEmpty();

        signInAs(UUID.randomUUID(), "CUSTOMER");
        assertThatThrownBy(() -> addressService.delete(address.id())).isInstanceOf(NotFoundException.class);
        assertThat(addressService.list()).isEmpty();
    }

    @Test
    void should_refuse_to_block_an_admin_including_yourself() {
        var admin = userApi.createUser(new NewUser("admin-" + UUID.randomUUID() + "@example.com", "Admin", null, Role.ADMIN));
        signInAs(admin.id(), "ADMIN");

        assertThatThrownBy(() -> userService.block(admin.id(), "oops"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.CANNOT_BLOCK_ADMIN);
    }

    @Test
    void should_refuse_more_than_twenty_addresses_per_customer() {
        var customer = userApi.createUser(new NewUser("many-" + UUID.randomUUID() + "@example.com", "Many", null, Role.CUSTOMER));
        signInAs(customer.id(), "CUSTOMER");
        var home = new Address.Details("Home", "12 MG Road", null, "Bengaluru", "Karnataka", "560001", 12.97, 77.59);
        for (int i = 0; i < 20; i++) {
            addressService.add(home);
        }

        assertThatThrownBy(() -> addressService.add(home))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.ADDRESS_LIMIT_REACHED);
    }

    private static void signInAs(UUID userId, String role) {
        var principal = new AuthenticatedUser(userId, role, Instant.now().plusSeconds(900));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
