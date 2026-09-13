package com.remidosol.llmmcp.e2e;

import com.remidosol.llmmcp.contracts.AggregateTypes;
import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventHeaders;
import com.remidosol.llmmcp.contracts.Topics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Reads the whole event trail of one job across the three topics, and injects hand-crafted events. */
final class Trail {

    private Trail() {
    }

    /** All envelopes for {@code jobId}, oldest first (by UUIDv7 event id = time). */
    static List<EventEnvelope> of(UUID jobId, Duration window) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Env.KAFKA);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        List<EventEnvelope> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(Topics.JOB_EVENTS, Topics.CREDIT_EVENTS, Topics.LLM_EVENTS));
            Instant deadline = Instant.now().plus(window);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (jobId.toString().equals(record.key())) {
                        found.add(EventEnvelope.fromJson(record.value()));
                    }
                }
            }
        }
        found.sort(Comparator.comparing(EventEnvelope::eventId));
        return found;
    }

    static List<String> types(List<EventEnvelope> trail) {
        return trail.stream().map(EventEnvelope::eventType).toList();
    }

    /** Publishes an envelope as if a service had emitted it (out-of-order injection). */
    static void inject(String topic, String eventType, UUID jobId, Object payload) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Env.KAFKA);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        EventEnvelope envelope = EventEnvelope.create(eventType, AggregateTypes.LLM, jobId.toString(), jobId.toString(),
                null, "llm-worker", payload);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, jobId.toString(), envelope.toJson());
            record.headers().add(EventHeaders.EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        } catch (Exception e) {
            throw new AssertionError("inject failed", e);
        }
    }

    static Map<String, Long> count(List<EventEnvelope> trail) {
        Map<String, Long> counts = new java.util.TreeMap<>();
        trail.forEach(e -> counts.merge(e.eventType(), 1L, Long::sum));
        return counts;
    }
}
