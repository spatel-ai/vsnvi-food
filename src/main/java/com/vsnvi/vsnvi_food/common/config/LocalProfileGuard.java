package com.vsnvi.vsnvi_food.common.config;

import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The {@code local} profile is the default and carries throwaway credentials, including a committed JWT key. A
 * deployment that forgets {@code SPRING_PROFILES_ACTIVE} would run with them, so refuse to start {@code local} against
 * a database that isn't on this machine.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
class LocalProfileGuard implements InitializingBean {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "host.docker.internal");

    private final String datasourceUrl;

    LocalProfileGuard(@Value("${spring.datasource.url:}") String datasourceUrl) {
        this.datasourceUrl = datasourceUrl;
    }

    @Override
    public void afterPropertiesSet() {
        if (datasourceUrl.isBlank()) {
            return;
        }
        var host = URI.create(datasourceUrl.substring("jdbc:".length())).getHost();
        if (host != null && !LOCAL_HOSTS.contains(host)) {
            throw new IllegalStateException(
                    "The 'local' profile (the default) must not run against a remote database. Set SPRING_PROFILES_ACTIVE"
                            + " to dev or prod for deployments.");
        }
    }
}
