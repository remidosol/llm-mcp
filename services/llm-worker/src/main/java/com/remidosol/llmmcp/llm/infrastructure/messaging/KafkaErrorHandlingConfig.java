package com.remidosol.llmmcp.llm.infrastructure.messaging;

import com.remidosol.llmmcp.contracts.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Retry + dead-letter policy for every listener in this service (PRD §4.8, ADR-0008): exponential
 * backoff 1 s → 10 s, 5 attempts in total, then the record goes to {@code <topic>.DLT}.
 * {@link NonRetryableException}s skip the retries. Boot picks up the single
 * {@code CommonErrorHandler} bean for its auto-configured listener container factory.
 */
@Configuration
class KafkaErrorHandlingConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, MeterRegistry registry) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template, (record, ex) -> {
            registry.counter("dlt.messages", "topic", record.topic()).increment();
            // DLTs have ONE partition (PRD §4.3); the default resolver would reuse the source partition
            return new TopicPartition(Topics.dlt(record.topic()), 0);
        });

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(4); // 4 retries = 5 attempts
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(NonRetryableException.class);
        return handler;
    }
}
