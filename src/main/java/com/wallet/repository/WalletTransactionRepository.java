package com.wallet.repository;

import com.wallet.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount + t.fee), 0) FROM WalletTransaction t " +
            "WHERE t.wallet.id = :walletId " +
            "AND t.type = 'DEBIT' " +
            "AND t.status = 'SUCCESS' " +
            "AND DATE(t.createdAt) = :today")
    BigDecimal sumTodayDebits(@Param("walletId") UUID walletId,
                              @Param("today") LocalDate today);
}
