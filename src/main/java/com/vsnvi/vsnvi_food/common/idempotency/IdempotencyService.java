package com.vsnvi.vsnvi_food.common.idempotency;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import com.vsnvi.vsnvi_food.common.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code Idempotency-Key} support (docs/API-DESIGN.md §6). Application services wrap a non-idempotent command:
 *
 * <pre>{@code
 * return idempotency.execute(idempotencyKey, "placeOrder", request, OrderView.class, () -> place(request));
 * }</pre>
 *
 * <ul>
 *   <li>First use: the key is claimed for the caller in its own transaction, the action runs, and its result is stored
 *       for 24 hours.
 *   <li>Same key, same request: the stored result is returned and the action does not run again.
 *   <li>Same key, different request, or a retry while the first request is still running: {@code 409
 *       IDEMPOTENCY_KEY_CONFLICT}.
 *   <li>The action fails: the claim is released, so the client can retry with the same key.
 * </ul>
 *
 * Keys are scoped to the authenticated caller. The action must run in its own transaction(s), never inside one held
 * by the caller of this method.
 */
@Slf4j
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyRecordRepository records;
    private final CurrentUser currentUser;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    IdempotencyService(
            IdempotencyRecordRepository records,
            CurrentUser currentUser,
            ObjectMapper json,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.records = records;
        this.currentUser = currentUser;
        this.json = json;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(String rawKey, String operation, Object request, Class<T> responseType, Supplier<T> action) {
        var key = parseKey(rawKey);
        var userId = currentUser.id();
        var requestHash = hash(operation, request);

        var stored = newTransaction.execute(status -> claim(userId, key, operation, requestHash));
        if (stored != null) {
            return json.readValue(stored, responseType);
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException ex) {
            newTransaction.executeWithoutResult(status -> records.findByUserIdAndKey(userId, key)
                    .filter(r -> !r.isCompleted())
                    .ifPresent(records::delete));
            throw ex;
        }

        var body = json.writeValueAsString(result);
        newTransaction.executeWithoutResult(
                status -> records.findByUserIdAndKey(userId, key).ifPresent(r -> r.complete(body)));
        return result;
    }

    /** @return the stored response body to replay, or null when the key was claimed and the action must run */
    private String claim(UUID userId, UUID key, String operation, String requestHash) {
        var now = clock.instant();
        var existing = records.findByUserIdAndKey(userId, key);
        if (existing.isPresent()) {
            var record = existing.get();
            if (record.isExpired(now)) {
                records.delete(record);
                records.flush();
            } else if (!record.matches(operation, requestHash)) {
                throw new IdempotencyConflictException(
                        "This Idempotency-Key was already used with a different request");
            } else if (!record.isCompleted()) {
                throw new IdempotencyConflictException("A request with this Idempotency-Key is still being processed");
            } else {
                return record.getResponseBody();
            }
        }
        try {
            records.saveAndFlush(IdempotencyRecord.claim(userId, key, operation, requestHash, now.plus(RETENTION)));
        } catch (DataIntegrityViolationException ex) {
            throw new IdempotencyConflictException("A request with this Idempotency-Key is still being processed");
        }
        return null;
    }

    /** Hourly; deleting is idempotent, so running on several instances at once is harmless. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void deleteExpired() {
        int deleted = newTransaction.execute(status -> records.deleteExpired(clock.instant()));
        if (deleted > 0) {
            log.info("Deleted {} expired idempotency key(s)", deleted);
        }
    }

    private static UUID parseKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new ValidationException("The Idempotency-Key header is required");
        }
        try {
            return UUID.fromString(rawKey.strip());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("The Idempotency-Key header must be a UUID");
        }
    }

    private String hash(String operation, Object request) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(operation.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(json.writeValueAsBytes(request));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }

    static class IdempotencyConflictException extends DomainException {

        IdempotencyConflictException(String message) {
            super(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, message);
        }
    }
}
