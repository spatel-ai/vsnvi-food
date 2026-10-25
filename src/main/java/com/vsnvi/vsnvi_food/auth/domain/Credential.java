package com.vsnvi.vsnvi_food.auth.domain;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A user's password hash. Never log it. */
@Entity
@Table(name = "credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Credential extends BaseEntity {

    private UUID userId;
    private String passwordHash;

    public static Credential create(UUID userId, String passwordHash) {
        var credential = new Credential();
        credential.userId = Objects.requireNonNull(userId, "userId");
        credential.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        return credential;
    }
}
