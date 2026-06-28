package com.wallet.repository;

import com.wallet.entity.GlAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GlAccountRepository extends JpaRepository<GlAccount, UUID> {
    Optional<GlAccount> findByCode(String code);
}
