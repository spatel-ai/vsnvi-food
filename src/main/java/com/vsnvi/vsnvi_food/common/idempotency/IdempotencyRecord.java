package com.vsnvi.vsnvi_food.common.idempotency;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One {@code Idempotency-Key} of one caller: claimed while the request runs, then holding the response. */
@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class IdempotencyRecord extends BaseEntity {

    enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    private UUID userId;

    @Column(name = "idempotency_key")
    private UUID key;

    private String operation;
    private String requestHash;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String responseBody;
    private Instant expiresAt;

    static IdempotencyRecord claim(UUID userId, UUID key, String operation, String requestHash, Instant expiresAt) {
        var record = new IdempotencyRecord();
        record.userId = Objects.requireNonNull(userId, "userId");
        record.key = Objects.requireNonNull(key, "key");
        record.operation = Objects.requireNonNull(operation, "operation");
        record.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        record.status = Status.IN_PROGRESS;
        record.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        return record;
    }

    boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    boolean matches(String operation, String requestHash) {
        return this.operation.equals(operation) && this.requestHash.equals(requestHash);
    }

    void complete(String responseBody) {
        this.status = Status.COMPLETED;
        this.responseBody = responseBody;
    }
}
