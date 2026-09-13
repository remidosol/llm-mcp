package com.remidosol.llmmcp.contracts;

import com.remidosol.llmmcp.contracts.event.JobCreated;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A producer may add fields at any time; consumers must not break. */
class TolerantReaderTest {

    @Test
    void unknown_fields_on_envelope_and_payload_are_ignored() {
        String json = """
                {"eventId":"019212f3-2b1e-7c1a-9d3f-1f2a3b4c5d01","eventType":"JobCreated","eventVersion":1,
                 "occurredAt":"2026-09-01T10:00:00Z","aggregateType":"job","aggregateId":"a","correlationId":"a",
                 "causationId":null,"producer":"job-service","newEnvelopeField":"ignored",
                 "payload":{"jobId":"6e2c5f6b-3a52-7b1e-8f2d-9c4e5a6b7c8d","userId":"u1","prompt":"p",
                            "model":"fake:demo","estimatedCredits":1,"newPayloadField":42}}
                """;

        EventEnvelope envelope = EventEnvelope.fromJson(json);
        JobCreated payload = envelope.payloadAs(JobCreated.class);

        assertThat(envelope.eventType()).isEqualTo(EventTypes.JOB_CREATED);
        assertThat(payload.userId()).isEqualTo("u1");
        assertThat(payload.estimatedCredits()).isEqualTo(1);
    }
}
