package com.remidosol.llmmcp.credit.domain;

/** Lifecycle of a reservation: RESERVED is the only non-terminal state. */
public enum ReservationStatus {
    RESERVED,
    CAPTURED,
    RELEASED
}
