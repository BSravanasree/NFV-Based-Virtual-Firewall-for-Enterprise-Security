package com.nexus.nfv.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs (monitoring, telemetry collection, detection, retention). Disabled in automated tests. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "nexus.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
