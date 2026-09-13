package com.remidosol.llmmcp.credit.domain;

/** Raised when a reservation would exceed the available credits — a business outcome, not a bug. */
public class InsufficientCreditsException extends RuntimeException {

    private final long available;
    private final long requested;

    public InsufficientCreditsException(long available, long requested) {
        super("insufficient credits: available=%d requested=%d".formatted(available, requested));
        this.available = available;
        this.requested = requested;
    }

    public long available() {
        return available;
    }

    public long requested() {
        return requested;
    }
}
