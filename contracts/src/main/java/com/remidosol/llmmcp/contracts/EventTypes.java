package com.remidosol.llmmcp.contracts;

import com.remidosol.llmmcp.contracts.event.CreditCaptured;
import com.remidosol.llmmcp.contracts.event.CreditRejected;
import com.remidosol.llmmcp.contracts.event.CreditReleased;
import com.remidosol.llmmcp.contracts.event.CreditReserved;
import com.remidosol.llmmcp.contracts.event.JobCompleted;
import com.remidosol.llmmcp.contracts.event.JobCreated;
import com.remidosol.llmmcp.contracts.event.JobFailed;
import com.remidosol.llmmcp.contracts.event.JobRejected;
import com.remidosol.llmmcp.contracts.event.JobTimedOut;
import com.remidosol.llmmcp.contracts.event.LlmFailed;
import com.remidosol.llmmcp.contracts.event.LlmStarted;
import com.remidosol.llmmcp.contracts.event.LlmSucceeded;

import java.util.Map;
import java.util.Optional;

/**
 * The event-type vocabulary. Strings, not classes, are the contract: consumers switch on these
 * constants and must skip (log, not fail) any value they do not know.
 */
public final class EventTypes {

    public static final String JOB_CREATED = "JobCreated";
    public static final String JOB_REJECTED = "JobRejected";
    public static final String JOB_COMPLETED = "JobCompleted";
    public static final String JOB_FAILED = "JobFailed";
    public static final String JOB_TIMED_OUT = "JobTimedOut";
    public static final String CREDIT_RESERVED = "CreditReserved";
    public static final String CREDIT_REJECTED = "CreditRejected";
    public static final String CREDIT_CAPTURED = "CreditCaptured";
    public static final String CREDIT_RELEASED = "CreditReleased";
    public static final String LLM_STARTED = "LlmStarted";
    public static final String LLM_SUCCEEDED = "LlmSucceeded";
    public static final String LLM_FAILED = "LlmFailed";

    /** eventType → payload record; also drives the schema/fixture tests. */
    public static final Map<String, Class<?>> PAYLOAD_TYPES = Map.ofEntries(
            Map.entry(JOB_CREATED, JobCreated.class),
            Map.entry(JOB_REJECTED, JobRejected.class),
            Map.entry(JOB_COMPLETED, JobCompleted.class),
            Map.entry(JOB_FAILED, JobFailed.class),
            Map.entry(JOB_TIMED_OUT, JobTimedOut.class),
            Map.entry(CREDIT_RESERVED, CreditReserved.class),
            Map.entry(CREDIT_REJECTED, CreditRejected.class),
            Map.entry(CREDIT_CAPTURED, CreditCaptured.class),
            Map.entry(CREDIT_RELEASED, CreditReleased.class),
            Map.entry(LLM_STARTED, LlmStarted.class),
            Map.entry(LLM_SUCCEEDED, LlmSucceeded.class),
            Map.entry(LLM_FAILED, LlmFailed.class));

    private EventTypes() {
    }

    public static Optional<Class<?>> payloadTypeOf(String eventType) {
        return Optional.ofNullable(PAYLOAD_TYPES.get(eventType));
    }
}
