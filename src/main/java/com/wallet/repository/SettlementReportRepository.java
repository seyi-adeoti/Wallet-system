package com.wallet.repository;

import com.wallet.entity.AmlFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SettlementReportRepository extends JpaRepository<SettlementReport, UUID> {
  
}

