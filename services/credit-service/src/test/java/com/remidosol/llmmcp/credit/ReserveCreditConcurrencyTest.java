package com.remidosol.llmmcp.credit;

import com.remidosol.llmmcp.credit.application.ReserveCreditService;
import com.remidosol.llmmcp.credit.application.TopUpService;
import com.remidosol.llmmcp.credit.application.port.CreditAccountRepository;
import com.remidosol.llmmcp.credit.application.port.CreditReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static com.remidosol.llmmcp.credit.KafkaTestSupport.jobCreated;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lost-update anomaly, provoked on purpose: N threads try to reserve 60 of 100 credits for the
 * same user at the same instant. Without {@code SELECT … FOR UPDATE} several would read
 * available=100 and all succeed; with it, exactly one wins.
 */
class ReserveCreditConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREADS = 6;

    @Autowired
    private ReserveCreditService reserve;

    @Autowired
    private TopUpService topUp;

    @Autowired
    private CreditAccountRepository accounts;

    @Autowired
    private CreditReservationRepository reservations;

    @Test
    void concurrent_reservations_on_one_account_never_over_commit() throws Exception {
        String user = "race-" + UUID.randomUUID();
        topUp.topUp(user, 100);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            var futures = IntStream.range(0, THREADS).mapToObj(i -> pool.submit(() -> {
                start.await();
                reserve.reserve(jobCreated(UUID.randomUUID(), user, 60));
                return null;
            })).toList();
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        var account = accounts.findByUserId(user).orElseThrow();
        assertThat(account.getReserved()).as("only one 60-credit hold fits into 100").isEqualTo(60);
        assertThat(account.available()).isEqualTo(40);
    }
}
