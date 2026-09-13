package com.remidosol.llmmcp.contracts;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * The single wire format for every Kafka record in the system (PRD §4.3): one JSON object, no
 * Spring type headers. Dispatch is by {@code eventType} string, never by Java class, so services
 * can evolve independently. {@code correlationId} is the job id for the whole saga; {@code
 * causationId} is the id of the event that triggered this one — together they let a trace be
 * reconstructed from the topics alone.
 *
 * @param payload the typed event body as a JSON tree; convert with {@link #payloadAs(Class)}
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String aggregateType,
        String aggregateId,
        String correlationId,
        UUID causationId,
        String producer,
        JsonNode payload
) {

    public static final int CURRENT_VERSION = 1;

    public static EventEnvelope create(String eventType,
                                       String aggregateType,
                                       String aggregateId,
                                       String correlationId,
                                       UUID causationId,
                                       String producer,
                                       Object payload) {
        return new EventEnvelope(
                UuidV7.generate(),
                eventType,
                CURRENT_VERSION,
                Instant.now(),
                aggregateType,
                aggregateId,
                correlationId,
                causationId,
                producer,
                canonicalTree(payload));
    }

    /**
     * Serialize-then-parse instead of {@code valueToTree}: the in-memory tree then has exactly the
     * node types a consumer will see after reading the JSON (e.g. small longs become IntNode), so
     * envelope equality holds across the wire — which is what the round-trip tests assert.
     */
    private static JsonNode canonicalTree(Object payload) {
        return ContractsJson.MAPPER.readTree(ContractsJson.MAPPER.writeValueAsString(payload));
    }

    public <T> T payloadAs(Class<T> type) {
        return ContractsJson.MAPPER.treeToValue(payload, type);
    }

    public String toJson() {
        return ContractsJson.MAPPER.writeValueAsString(this);
    }

    public static EventEnvelope fromJson(String json) {
        return ContractsJson.MAPPER.readValue(json, EventEnvelope.class);
    }
}
