package com.remidosol.llmmcp.contracts;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one JSON mapper every producer and consumer uses for event envelopes. It exists so the wire
 * format is defined in exactly one place: ISO-8601 timestamps, and a tolerant reader — unknown
 * fields are ignored so a producer can add fields without breaking older consumers.
 */
public final class ContractsJson {

    public static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS) // Jackson 3 moved date features to DateTimeFeature
            .build();

    private ContractsJson() {
    }
}
