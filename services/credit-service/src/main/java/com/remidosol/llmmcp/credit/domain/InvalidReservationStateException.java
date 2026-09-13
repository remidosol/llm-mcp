package com.remidosol.llmmcp.credit.domain;

/** A capture/release on a reservation that is no longer RESERVED (duplicate or out-of-order event). */
public class InvalidReservationStateException extends RuntimeException {

    public InvalidReservationStateException(ReservationStatus current, String attempted) {
        super("reservation is %s; cannot %s".formatted(current, attempted));
    }
}
