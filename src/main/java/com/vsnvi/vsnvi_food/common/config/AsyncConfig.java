package com.vsnvi.vsnvi_food.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** {@code @ApplicationModuleListener} runs listeners asynchronously after commit, which needs {@code @EnableAsync}. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {}
