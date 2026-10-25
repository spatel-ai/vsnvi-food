package com.vsnvi.vsnvi_food.common.web;

import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Cursor pagination envelope from {@code docs/API-DESIGN.md} §5, for fast-growing feeds such as order history.
 * {@code nextCursor} is absent on the last page. Cursors are opaque to clients: a base64url-encoded
 * {@code (createdAt, id)} position for keyset queries like {@code WHERE (created_at, id) < (:createdAt, :id)}.
 */
public record CursorPageResponse<T>(List<T> content, String nextCursor) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    /**
     * Builds a page from a query that fetched up to {@code limit + 1} rows: the extra row only signals that another
     * page exists.
     */
    public static <E, T> CursorPageResponse<T> of(
            List<E> rowsPlusOne, int limit, Function<E, Position> positionOf, Function<? super E, ? extends T> mapper) {
        var hasMore = rowsPlusOne.size() > limit;
        var page = hasMore ? rowsPlusOne.subList(0, limit) : rowsPlusOne;
        var next = hasMore ? positionOf.apply(page.getLast()).encode() : null;
        return new CursorPageResponse<>(page.stream().<T>map(mapper).toList(), next);
    }

    /** Clamps a client's {@code limit} to 1..100, defaulting to 20. */
    public static int limit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested < 1 || requested > MAX_LIMIT) {
            throw new ValidationException("limit must be between 1 and " + MAX_LIMIT);
        }
        return requested;
    }

    /** The last row a client has seen. */
    public record Position(Instant createdAt, UUID id) {

        public String encode() {
            var raw = createdAt.getEpochSecond() + "." + createdAt.getNano() + ":" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        /** @return null for a null cursor (the first page) */
        public static Position decode(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return null;
            }
            try {
                var raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                var colon = raw.indexOf(':');
                var dot = raw.indexOf('.');
                if (colon < 0 || dot < 0 || dot > colon) {
                    throw new IllegalArgumentException();
                }
                var instant = Instant.ofEpochSecond(
                        Long.parseLong(raw.substring(0, dot)), Long.parseLong(raw.substring(dot + 1, colon)));
                return new Position(instant, UUID.fromString(raw.substring(colon + 1)));
            } catch (IllegalArgumentException | DateTimeException ex) {
                // Covers bad base64, numbers and UUIDs. The cursor isn't echoed back.
                throw new ValidationException("Invalid cursor");
            }
        }
    }
}
