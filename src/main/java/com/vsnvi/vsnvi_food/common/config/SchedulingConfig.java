package com.vsnvi.vsnvi_food.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduled jobs (idempotency key cleanup now; payment timeouts and the GEO sweeper later). Single-run locking across
 * instances (e.g. ShedLock) is a Phase 5 item; until then every job must be safe to run on each instance.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
