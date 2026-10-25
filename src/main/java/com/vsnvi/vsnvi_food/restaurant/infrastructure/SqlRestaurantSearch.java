package com.vsnvi.vsnvi_food.restaurant.infrastructure;

import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import com.vsnvi.vsnvi_food.restaurant.domain.RestaurantSearch;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Search in one SQL statement so filtering, sorting and paging stay in the database. Only the filters that are present
 * are added, and every value is a bind parameter. Open-now uses the same half-open slot rules as
 * {@code OpeningHours.covers}.
 */
@Component
@RequiredArgsConstructor
class SqlRestaurantSearch implements RestaurantSearch {

    /** Haversine distance in km from the bind point (:lat, :lng). */
    private static final String DISTANCE = """
            (6371 * 2 * asin(sqrt(
                power(sin(radians(r.latitude - :lat) / 2), 2)
                + cos(radians(:lat)) * cos(radians(r.latitude)) * power(sin(radians(r.longitude - :lng) / 2), 2))))""";

    /** Local time of :now in the restaurant's zone, matched against today's slots and yesterday's overnight slots. */
    private static final String OPEN_NOW = """
            (NOT r.temporarily_closed AND EXISTS (
                SELECT 1 FROM opening_hours h
                CROSS JOIN LATERAL (SELECT (CAST(:now AS timestamptz) AT TIME ZONE r.time_zone) AS lt) l
                WHERE h.restaurant_id = r.id AND (
                    (h.opens_at < h.closes_at AND h.day_of_week = EXTRACT(ISODOW FROM l.lt)
                        AND CAST(l.lt AS time) >= h.opens_at AND CAST(l.lt AS time) < h.closes_at)
                    OR (h.opens_at > h.closes_at AND h.day_of_week = EXTRACT(ISODOW FROM l.lt)
                        AND CAST(l.lt AS time) >= h.opens_at)
                    OR (h.opens_at > h.closes_at AND h.day_of_week = (CAST(EXTRACT(ISODOW FROM l.lt) AS int) + 5) % 7 + 1
                        AND CAST(l.lt AS time) < h.closes_at))))""";

    private final JdbcClient jdbc;

    @Override
    public Page<Hit> search(Criteria criteria, Instant now, Pageable pageable) {
        var params = new HashMap<String, Object>();
        params.put("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        var where = new StringBuilder(" FROM restaurants r WHERE r.status = 'APPROVED'");
        if (criteria.cuisine() != null) {
            where.append(" AND :cuisine = ANY(r.cuisines)");
            params.put("cuisine", criteria.cuisine());
        }
        if (criteria.minRating() != null) {
            where.append(" AND r.rating >= :minRating");
            params.put("minRating", criteria.minRating());
        }
        if (criteria.query() != null) {
            where.append(" AND r.name ILIKE :q ESCAPE '\\'");
            params.put("q", "%" + escapeLike(criteria.query()) + "%");
        }
        if (criteria.hasLocation()) {
            where.append(" AND ").append(DISTANCE).append(" <= r.serviceable_radius_km");
            params.put("lat", criteria.latitude());
            params.put("lng", criteria.longitude());
        }
        if (criteria.isOpen() != null) {
            where.append(" AND ").append(OPEN_NOW).append(" = :isOpen");
            params.put("isOpen", criteria.isOpen());
        }

        long total = jdbc.sql("SELECT count(*)" + where)
                .params(params)
                .query(Long.class)
                .single();
        if (total == 0) {
            return Page.empty(pageable);
        }

        var select = "SELECT r.id, " + OPEN_NOW + " AS open_now, "
                + (criteria.hasLocation() ? DISTANCE : "CAST(NULL AS double precision)") + " AS distance_km";
        var sql = select + where + orderBy(pageable.getSort(), criteria) + " LIMIT :limit OFFSET :offset";
        params.put("limit", pageable.getPageSize());
        params.put("offset", pageable.getOffset());

        var hits = jdbc.sql(sql)
                .params(params)
                .query((rs, n) -> new Hit(
                        rs.getObject("id", UUID.class),
                        (Double) rs.getObject("distance_km"),
                        rs.getBoolean("open_now")))
                .list();
        return new PageImpl<>(hits, pageable, total);
    }

    /** Maps the client's sort to whitelisted SQL; defaults to distance with a location, else name. */
    private static String orderBy(Sort sort, Criteria criteria) {
        var clauses = new ArrayList<String>();
        for (var order : sort) {
            var direction = order.isAscending() ? "ASC" : "DESC";
            switch (order.getProperty()) {
                case SORT_DISTANCE -> {
                    if (!criteria.hasLocation()) {
                        throw new ValidationException("Sorting by distance needs lat and lng");
                    }
                    clauses.add("distance_km " + direction);
                }
                case SORT_RATING -> clauses.add("r.rating " + direction + " NULLS LAST");
                case SORT_NAME -> clauses.add("r.name " + direction);
                default -> throw new ValidationException("Unsupported sort field; allowed: distance, rating, name");
            }
        }
        if (clauses.isEmpty()) {
            clauses.add(criteria.hasLocation() ? "distance_km ASC" : "r.name ASC");
        }
        clauses.add("r.id ASC"); // stable paging
        return " ORDER BY " + String.join(", ", clauses);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
