package com.wallet.repository;

import com.wallet.entity.KycUpgrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface KycUpgradeRepository extends JpaRepository<KycUpgrade, UUID> {
    List<KycUpgrade> findByUserId(UUID userId);
}