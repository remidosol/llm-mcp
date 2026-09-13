package com.remidosol.llmmcp.credit.infrastructure.persistence;

import com.remidosol.llmmcp.credit.application.port.CreditAccountRepository;
import com.remidosol.llmmcp.credit.domain.CreditAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Persistence adapter for accounts. {@code @Lock(PESSIMISTIC_WRITE)} makes Hibernate append
 * {@code FOR UPDATE} to the select: the row stays locked until the surrounding transaction ends,
 * which is exactly the "one reservation at a time per user" guarantee the saga needs.
 */
public interface CreditAccountJpaRepository extends JpaRepository<CreditAccount, String>, CreditAccountRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditAccount a where a.userId = :userId")
    Optional<CreditAccount> findForUpdate(@Param("userId") String userId);
}
