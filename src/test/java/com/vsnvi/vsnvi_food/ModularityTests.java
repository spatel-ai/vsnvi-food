package com.vsnvi.vsnvi_food;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build on access to another module's internals and on module dependency cycles. Never weaken it. */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(VsnviFoodApplication.class);

    @Test
    void should_respect_module_boundaries_when_verifying_the_module_graph() {
        modules.verify();
    }
}
