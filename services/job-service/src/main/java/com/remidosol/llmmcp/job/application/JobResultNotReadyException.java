package com.remidosol.llmmcp.job.application;

import java.util.UUID;

/** The job exists but is not COMPLETED: the API answers 404 until it is. */
public class JobResultNotReadyException extends RuntimeException {

    public JobResultNotReadyException(UUID jobId) {
        super("Result not available yet for job " + jobId);
    }
}
