package com.remidosol.llmmcp.llm.infrastructure.messaging;

/** Logical consumer identity for the inbox; the Kafka group id may differ (replay chaos exercise). */
public final class ConsumerGroups {

    public static final String LLM_WORKER = "llm-worker";

    private ConsumerGroups() {
    }
}
