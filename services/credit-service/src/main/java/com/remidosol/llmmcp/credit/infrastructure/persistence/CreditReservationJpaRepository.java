package com.remidosol.llmmcp.credit.infrastructure.persistence;

import com.remidosol.llmmcp.credit.application.port.CreditReservationRepository;
import com.remidosol.llmmcp.credit.domain.CreditReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Persistence adapter for reservations; {@code findByJobId} is a Spring Data derived query. */
public interface CreditReservationJpaRepository
        extends JpaRepository<CreditReservation, UUID>, CreditReservationRepository {
}
