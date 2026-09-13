package com.remidosol.llmmcp.job.api.error;

import com.remidosol.llmmcp.job.application.JobNotFoundException;
import com.remidosol.llmmcp.job.application.JobResultNotReadyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps application exceptions to RFC 9457 ProblemDetail bodies. Only cases Spring cannot infer
 * belong here — validation errors and missing headers already become problem+json via
 * {@code spring.mvc.problemdetails.enabled=true}.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    ProblemDetail jobNotFound(JobNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Job not found");
        return problem;
    }

    @ExceptionHandler(JobResultNotReadyException.class)
    ProblemDetail resultNotReady(JobResultNotReadyException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Result not ready");
        return problem;
    }
}
