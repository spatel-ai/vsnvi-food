package com.vsnvi.vsnvi_food.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LocalProfileGuardTest {

    @Test
    void should_start_when_local_profile_uses_local_database() {
        assertThatCode(() ->
                        new LocalProfileGuard("jdbc:postgresql://localhost:5432/vsnvi_food", "").afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatCode(() -> new LocalProfileGuard("", "").afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void should_refuse_to_start_when_local_profile_points_at_remote_database() {
        assertThatThrownBy(() -> new LocalProfileGuard("jdbc:postgresql://db.prod.internal:5432/vsnvi_food", "")
                        .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVE");
    }

    @Test
    void should_refuse_to_start_when_database_host_cannot_be_read() {
        assertThatThrownBy(() ->
                        new LocalProfileGuard("jdbc:postgresql://h1:5432,h2:5432/vsnvi_food", "").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVE");
        assertThatThrownBy(() -> new LocalProfileGuard("jdbc", "").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_refuse_to_start_when_running_on_kubernetes() {
        assertThatThrownBy(() -> new LocalProfileGuard("jdbc:postgresql://localhost:5432/vsnvi_food", "10.96.0.1")
                        .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kubernetes");
    }
}
