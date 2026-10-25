package com.vsnvi.vsnvi_food.common.config;

import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The {@code local} profile is the default and carries throwaway credentials, including a committed JWT key. A
 * deployment that forgets {@code SPRING_PROFILES_ACTIVE} would run with them, so refuse to start {@code local} on
 * Kubernetes, or against a database that isn't on this machine or whose host can't be read from the URL.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
class LocalProfileGuard implements InitializingBean {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "host.docker.internal");

    private final String datasourceUrl;
    private final String kubernetesServiceHost;

    LocalProfileGuard(
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${KUBERNETES_SERVICE_HOST:}") String kubernetesServiceHost) {
        this.datasourceUrl = datasourceUrl;
        this.kubernetesServiceHost = kubernetesServiceHost;
    }

    @Override
    public void afterPropertiesSet() {
        if (!kubernetesServiceHost.isBlank()) {
            throw refuse("must not run on Kubernetes");
        }
        // Blank when a Testcontainers service connection supplies the database instead.
        if (datasourceUrl.isBlank()) {
            return;
        }
        var host = hostOf(datasourceUrl);
        if (host == null || !LOCAL_HOSTS.contains(host)) {
            throw refuse("must not run against a remote database");
        }
    }

    /** {@code null} when the URL has no single readable host, e.g. a multi-host URL. */
    private static String hostOf(String jdbcUrl) {
        try {
            return URI.create(jdbcUrl.substring("jdbc:".length())).getHost();
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            return null;
        }
    }

    private static IllegalStateException refuse(String reason) {
        return new IllegalStateException("The 'local' profile (the default) " + reason
                + ". Set SPRING_PROFILES_ACTIVE to dev or prod for deployments.");
    }
}
