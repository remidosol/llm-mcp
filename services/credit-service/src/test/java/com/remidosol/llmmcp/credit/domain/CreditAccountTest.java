package com.remidosol.llmmcp.credit.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The semantic lock arithmetic: available = balance - reserved through reserve/capture/release. */
class CreditAccountTest {

    @Test
    void reserve_moves_credits_from_available_to_reserved_without_deducting() {
        CreditAccount account = CreditAccount.open("u1", 100);

        account.reserve(30);

        assertThat(account.getBalance()).isEqualTo(100);
        assertThat(account.getReserved()).isEqualTo(30);
        assertThat(account.available()).isEqualTo(70);
    }

    @Test
    void reserve_beyond_available_fails_and_changes_nothing() {
        CreditAccount account = CreditAccount.open("u1", 100);
        account.reserve(80);

        assertThatThrownBy(() -> account.reserve(30))
                .isInstanceOf(InsufficientCreditsException.class)
                .hasMessageContaining("available=20");
        assertThat(account.getReserved()).isEqualTo(80);
    }

    @Test
    void capture_deducts_the_actual_cost_and_frees_the_whole_reservation() {
        CreditAccount account = CreditAccount.open("u1", 100);
        account.reserve(30);

        account.capture(30, 12); // partial capture: actual < estimated

        assertThat(account.getBalance()).isEqualTo(88);
        assertThat(account.getReserved()).isZero();
        assertThat(account.available()).isEqualTo(88);
    }

    @Test
    void release_frees_the_reservation_and_deducts_nothing() {
        CreditAccount account = CreditAccount.open("u1", 100);
        account.reserve(30);

        account.release(30);

        assertThat(account.getBalance()).isEqualTo(100);
        assertThat(account.available()).isEqualTo(100);
    }

    @Test
    void a_reservation_can_be_captured_or_released_only_once() {
        CreditReservation reservation = CreditReservation.create(java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), "u1", 30);
        reservation.capture(12);

        assertThatThrownBy(reservation::release).isInstanceOf(InvalidReservationStateException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CAPTURED);
        assertThat(reservation.getCapturedAmount()).isEqualTo(12);
    }
}
