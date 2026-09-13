package com.remidosol.llmmcp.contracts;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixtures are the frozen wire format: every consumer test in every service reuses them, so a
 * change that breaks a fixture is a contract change and needs an ADR + version bump.
 */
class FixtureCompatibilityTest {

    static Stream<String> eventTypes() {
        return EventTypes.PAYLOAD_TYPES.keySet().stream().sorted();
    }

    @ParameterizedTest(name = "{0}.json")
    @MethodSource("eventTypes")
    void fixture_deserializes_and_validates(String type) throws IOException {
        String json = fixture(type);

        EventEnvelope envelope = EventEnvelope.fromJson(json);
        JsonNode tree = ContractsJson.MAPPER.readTree(json);

        assertThat(envelope.eventType()).isEqualTo(type);
        assertThat(envelope.eventVersion()).isEqualTo(EventEnvelope.CURRENT_VERSION);
        assertThat(envelope.correlationId()).isEqualTo(envelope.aggregateId());
        assertThat(envelope.payloadAs(EventTypes.PAYLOAD_TYPES.get(type))).isNotNull();
        assertThat(SchemaSupport.validate(SchemaSupport.envelopeSchema(), tree)).isEmpty();
        assertThat(SchemaSupport.validate(SchemaSupport.payloadSchema(type), tree.get("payload"))).isEmpty();
    }

    static String fixture(String type) throws IOException {
        try (InputStream in = FixtureCompatibilityTest.class.getResourceAsStream("/fixtures/" + type + ".json")) {
            assertThat(in).as("fixture " + type).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
