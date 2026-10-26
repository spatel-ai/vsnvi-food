-- A GIN index entry is capped at ~2712 bytes, so an unbounded cuisine string fails the insert with
-- SQLState 54000. Bound each element (1-40 chars of lower-case kebab-case) and the list (1-10 items),
-- matching RegisterRestaurantRequest. CHECK can't use subqueries, so the joined array is matched:
-- one regex bounds each element's length, the other its kebab-case shape. array_to_string skips
-- NULL elements, so they are rejected separately.
ALTER TABLE restaurants
    ADD CONSTRAINT chk_restaurants_cuisines
        CHECK (cardinality(cuisines) BETWEEN 1 AND 10
           AND array_position(cuisines, NULL) IS NULL
           AND array_to_string(cuisines, ',') ~ '^[a-z-]{1,40}(,[a-z-]{1,40})*$'
           AND array_to_string(cuisines, ',') ~ '^[a-z]+(-[a-z]+)*(,[a-z]+(-[a-z]+)*)*$');
