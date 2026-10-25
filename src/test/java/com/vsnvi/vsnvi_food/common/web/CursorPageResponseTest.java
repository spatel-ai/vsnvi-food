package com.vsnvi.vsnvi_food.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorPageResponseTest {

    private record Row(Instant createdAt, UUID id) {}

    @Test
    void should_round_trip_position_with_nanoseconds_when_encoding_cursor() {
        var position =
                new CursorPageResponse.Position(Instant.parse("2026-10-25T10:00:00.123456789Z"), UUID.randomUUID());

        assertThat(CursorPageResponse.Position.decode(position.encode())).isEqualTo(position);
    }

    @Test
    void should_set_next_cursor_only_when_an_extra_row_was_fetched() {
        var rows = List.of(row(3), row(2), row(1));

        var firstPage =
                CursorPageResponse.of(rows, 2, r -> new CursorPageResponse.Position(r.createdAt(), r.id()), Row::id);
        var lastPage = CursorPageResponse.of(
                rows.subList(2, 3), 2, r -> new CursorPageResponse.Position(r.createdAt(), r.id()), Row::id);

        assertThat(firstPage.content()).hasSize(2);
        assertThat(CursorPageResponse.Position.decode(firstPage.nextCursor()).id())
                .isEqualTo(rows.get(1).id());
        assertThat(lastPage.nextCursor()).isNull();
    }

    @Test
    void should_reject_tampered_cursor_and_out_of_range_limit() {
        assertThatThrownBy(() -> CursorPageResponse.Position.decode("not-a-cursor"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> CursorPageResponse.limit(101)).isInstanceOf(ValidationException.class);
        assertThat(CursorPageResponse.limit(null)).isEqualTo(20);
        assertThat(CursorPageResponse.Position.decode(null)).isNull();
    }

    private static Row row(int minute) {
        return new Row(Instant.parse("2026-10-25T10:00:00Z").plusSeconds(minute * 60L), UUID.randomUUID());
    }
}
