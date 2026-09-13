package com.remidosol.llmmcp.job.infrastructure.messaging;

/** Logical consumer identity for the inbox; the Kafka group id may differ for replays. */
public final class ConsumerGroups {

    public static final String JOB_SERVICE = "job-service";

    private ConsumerGroups() {
    }
}
