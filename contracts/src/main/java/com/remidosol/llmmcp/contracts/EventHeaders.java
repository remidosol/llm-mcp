package com.remidosol.llmmcp.contracts;

/**
 * Kafka record header names. Headers let tooling (kafka-ui, DLT triage, tracing) read the event
 * type and id without parsing the JSON value (PRD §4.3).
 */
public final class EventHeaders {

    public static final String EVENT_TYPE = "eventType";
    public static final String EVENT_ID = "eventId";
    public static final String TRACEPARENT = "traceparent";

    private EventHeaders() {
    }
}
