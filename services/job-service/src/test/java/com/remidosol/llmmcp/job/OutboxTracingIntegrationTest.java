package com.remidosol.llmmcp.job;

import com.remidosol.llmmcp.contracts.Topics;
import com.remidosol.llmmcp.job.application.CreateJobService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.remidosol.llmmcp.job.JobEventPublishingIntegrationTest.awaitRecordWithKey;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0024: the trace that created the job must be the trace on the Kafka record, although the
 * outbox poller sends from another thread. @SpringBootTest disables tracing by default;
 * {@code @AutoConfigureTracing} switches the Tracer back on. Boot 4 wires W3C propagation only when
 * tracing EXPORT is enabled ({@code @ConditionalOnEnabledTracingExport}), so export is switched on
 * here while the OTLP exporters stay off — nothing is shipped anywhere.
 */
@AutoConfigureTracing
@TestPropertySource(properties = {
        "management.tracing.export.enabled=true",
        "management.tracing.export.otlp.enabled=false",
        "management.logging.export.otlp.enabled=false"
})
class OutboxTracingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private Tracer tracer;

    @Autowired
    private CreateJobService createJobService;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Test
    void traceparent_travels_from_the_writing_transaction_through_the_outbox_to_the_record() {
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("test-" + UUID.randomUUID(), "")) {
            consumer.subscribe(List.of(Topics.JOB_EVENTS));

            Span span = tracer.nextSpan().name("test create job").start();
            UUID jobId;
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                jobId = createJobService.create("trace-user", "trace me", "fake:demo").jobId();
            } finally {
                span.end();
            }
            String traceId = span.context().traceId();

            String stored = jdbc.sql("select traceparent from outbox where aggregateid = :id")
                    .param("id", jobId.toString()).query(String.class).single();
            assertThat(stored).as("outbox row keeps the writer's W3C context").startsWith("00-" + traceId + "-");

            ConsumerRecord<String, String> record = awaitRecordWithKey(consumer, jobId.toString());
            Header header = record.headers().lastHeader("traceparent");
            assertThat(header).as("KafkaTemplate observation put a traceparent header on the record").isNotNull();
            assertThat(new String(header.value(), StandardCharsets.UTF_8))
                    .as("same trace id, new span id (the poller's child span)")
                    .startsWith("00-" + traceId + "-")
                    .isNotEqualTo(stored);
        }
    }
}
