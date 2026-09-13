package com.remidosol.llmmcp.credit.application.port;

import com.remidosol.llmmcp.credit.domain.CreditReservation;

import java.util.Optional;
import java.util.UUID;

/** Outbound port for reservations. */
public interface CreditReservationRepository {

    CreditReservation save(CreditReservation reservation);

    Optional<CreditReservation> findByJobId(UUID jobId);
}
