package com.vsnvi.vsnvi_food;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build on access to another module's internals and on module dependency cycles. Never weaken it. */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(VsnviFoodApplication.class);

    @Test
    void should_respect_module_boundaries_when_verifying_the_module_graph() {
        modules.verify();
    }

    /** ADR-003: {@code @NamedInterface("api")} must cover only {@code api}, never its {@code api.web} sub-package. */
    @Test
    void should_keep_api_web_out_of_the_api_named_interface_when_module_has_one() {
        assertThat(modules.getModuleByName("user")
                        .orElseThrow()
                        .getNamedInterfaces()
                        .getByName("api"))
                .isPresent();
        modules.forEach(module -> module.getNamedInterfaces()
                .getByName("api")
                .ifPresent(api -> assertThat(api.asJavaClasses())
                        .as("api named interface of module %s", module.getIdentifier())
                        .allSatisfy(type -> assertThat(type.getPackageName()).doesNotContain(".api.web"))
                        .isNotEmpty()));
    }
}
