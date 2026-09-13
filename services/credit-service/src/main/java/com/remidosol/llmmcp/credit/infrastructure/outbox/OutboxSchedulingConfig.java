package com.remidosol.llmmcp.credit.infrastructure.outbox;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} for the poller and the retention job (virtual threads, Boot default). */
@Configuration
@EnableScheduling
class OutboxSchedulingConfig {
}
