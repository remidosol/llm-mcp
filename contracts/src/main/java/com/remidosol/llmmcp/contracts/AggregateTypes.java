package com.remidosol.llmmcp.contracts;

/**
 * {@code aggregateType} values. Lower-case on purpose: the Debezium outbox router (Phase 3) builds
 * the topic name from this column as {@code ${routedByValue}.events.v1}.
 */
public final class AggregateTypes {

    public static final String JOB = "job";
    public static final String CREDIT = "credit";
    public static final String LLM = "llm";

    private AggregateTypes() {
    }
}
