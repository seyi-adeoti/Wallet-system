package com.wallet.repository;

import com.wallet.entity.GlEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GlEntryRepository extends JpaRepository<GlEntry, UUID> {
    List<GlEntry> findByGlAccountId(UUID glAccountId);
    BigDecimal sumByAccountAndType(UUID glAccountId, String type);
    BigDecimal sumByAccountAndTypeAndDate(UUID glAccountId, String type, LocalDate date);
}
