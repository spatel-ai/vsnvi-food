package com.vsnvi.vsnvi_food;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VsnviFoodApplicationTests {

    private final ApplicationContext context;

    VsnviFoodApplicationTests(@Autowired ApplicationContext context) {
        this.context = context;
    }

    @Test
    void contextLoads() {}

    @Test
    void should_not_create_default_user_details_service_when_context_starts() {
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }
}
