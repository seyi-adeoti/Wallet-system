package com.wallet.service;

import com.wallet.entity.Wallet;
import com.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Enforces CBN-style KYC tier limits: per-transfer cap, daily debit cap, and max wallet balance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KycLimitService {

    private final WalletTransactionRepository transactionRepository;

    /**
     * Validates that a proposed transfer stays within the user's KYC tier limits.
     * Checks single-transfer limit, cumulative daily debits, and max wallet balance.
     */
    public void validateTransferLimit(Wallet wallet, BigDecimal amount) {
        String tier = wallet.getUser().getKycTier();
        log.debug("Validating KYC limits for walletId={} tier={} amount={}", wallet.getId(), tier, amount);
        BigDecimal singleLimit = getSingleTransferLimit(tier);
        BigDecimal dailyLimit = getDailyDebitLimit(tier);
        BigDecimal maxBalance = getMaxWalletBalance(tier);

        if (amount.compareTo(singleLimit) > 0) {
            log.warn("Single transfer limit exceeded: tier={} amount={} limit={}", tier, amount, singleLimit);
            throw new IllegalArgumentException("Transfer amount exceeds single transfer limit for your tier");
        }

        BigDecimal todayDebits = transactionRepository.sumTodayDebits(wallet.getId(), LocalDate.now());
        if (todayDebits.add(amount).compareTo(dailyLimit) > 0) {
            log.warn("Daily debit limit exceeded: tier={} todayDebits={} amount={} limit={}",
                    tier, todayDebits, amount, dailyLimit);
            throw new IllegalArgumentException("Transfer would exceed daily debit limit");
        }

        if (wallet.getBalance().compareTo(maxBalance) >= 0) {
            log.warn("Max wallet balance reached: tier={} balance={} max={}", tier, wallet.getBalance(), maxBalance);
            throw new IllegalArgumentException("Wallet balance exceeds allowed max for your tier");
        }
        log.debug("KYC validation passed for walletId={}", wallet.getId());
    }

    /** Returns the maximum amount allowed in a single transfer for the given tier. */
    private BigDecimal getSingleTransferLimit(String tier) {
        return switch (tier) {
            case "TIER_2" -> new BigDecimal("200000.00");
            case "TIER_3" -> new BigDecimal("1000000.00");
            default -> new BigDecimal("50000.00");
        };
    }

    /** Returns the maximum total debits allowed per calendar day for the given tier. */
    private BigDecimal getDailyDebitLimit(String tier) {
        return switch (tier) {
            case "TIER_2" -> new BigDecimal("500000.00");
            case "TIER_3" -> new BigDecimal("5000000.00");
            default -> new BigDecimal("300000.00");
        };
    }

    /** Returns the maximum wallet balance allowed for the given tier. */
    private BigDecimal getMaxWalletBalance(String tier) {
        return switch (tier) {
            case "TIER_2" -> new BigDecimal("500000.00");
            case "TIER_3" -> new BigDecimal("99999999.00");
            default -> new BigDecimal("300000.00");
        };
    }
}
