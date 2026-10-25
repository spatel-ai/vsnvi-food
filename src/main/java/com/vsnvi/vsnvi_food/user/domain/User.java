package com.vsnvi.vsnvi_food.user.domain;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    private String email;
    private String fullName;
    private String phone;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    public static User register(String email, String fullName, String phone, Role role) {
        var user = new User();
        user.email = normalizeEmail(email);
        user.fullName = requireText(fullName, "fullName");
        user.phone = phone;
        user.role = Objects.requireNonNull(role, "role");
        user.status = UserStatus.ACTIVE;
        return user;
    }

    public static String normalizeEmail(String email) {
        return requireText(email, "email").toLowerCase(Locale.ROOT);
    }

    /** Null arguments leave the field unchanged. Email and role can't be changed here. */
    public void updateProfile(String fullName, String phone) {
        if (fullName != null) {
            this.fullName = requireText(fullName, "fullName");
        }
        if (phone != null) {
            this.phone = phone;
        }
    }

    /** @return {@code true} if the user was active and is now blocked; {@code false} if already blocked. */
    public boolean block() {
        if (status == UserStatus.BLOCKED) {
            return false;
        }
        status = UserStatus.BLOCKED;
        return true;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
