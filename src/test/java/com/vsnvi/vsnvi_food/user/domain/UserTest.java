package com.vsnvi.vsnvi_food.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void should_lowercase_and_trim_email_when_registering() {
        var user = User.register("  Asha@Example.COM ", " Asha Rao ", null, Role.CUSTOMER);

        assertThat(user.getEmail()).isEqualTo("asha@example.com");
        assertThat(user.getFullName()).isEqualTo("Asha Rao");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void should_change_only_given_fields_when_updating_profile() {
        var user = User.register("a@example.com", "Asha", "+919876543210", Role.CUSTOMER);

        user.updateProfile(null, "+919000000000");

        assertThat(user.getFullName()).isEqualTo("Asha");
        assertThat(user.getPhone()).isEqualTo("+919000000000");
    }

    @Test
    void should_reject_blank_name_when_updating_profile() {
        var user = User.register("a@example.com", "Asha", null, Role.CUSTOMER);

        assertThatThrownBy(() -> user.updateProfile("  ", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_report_change_only_once_when_blocking_twice() {
        var user = User.register("a@example.com", "Asha", null, Role.CUSTOMER);

        assertThat(user.block()).isTrue();
        assertThat(user.block()).isFalse();
        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
    }
}
