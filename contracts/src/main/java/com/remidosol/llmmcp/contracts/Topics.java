package com.remidosol.llmmcp.contracts;

/**
 * Topic names carry the contract's major version: a breaking change ships as a new
 * topic, never as a surprise on the old one. Dead letters live next to their source topic.
 */
public final class Topics {

    public static final String JOB_EVENTS = "job.events.v1";
    public static final String CREDIT_EVENTS = "credit.events.v1";
    public static final String LLM_EVENTS = "llm.events.v1";
    public static final String DLT_SUFFIX = ".DLT";

    private Topics() {
    }

    public static String dlt(String topic) {
        return topic + DLT_SUFFIX;
    }
}
