package com.remidosol.llmmcp.job.application;

import java.util.UUID;

/**
 * Signals a lookup for a job that does not exist. Exists so the API layer can map exactly this
 * case to a 404 ProblemDetail without leaking persistence concerns upward.
 */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID jobId) {
        super("Job not found: " + jobId);
    }
}
