package com.wallet.service;

import com.wallet.entity.Wallet;
import com.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class KycLimitService {

    private final WalletTransactionRepository transactionRepository;

    public void validateTransferLimit(Wallet wallet, BigDecimal amount) {
        String tier = wallet.getUser().getKycTier();
        BigDecimal singleLimit = getSingleTransferLimit(tier);
        BigDecimal dailyLimit = getDailyDebitLimit(tier);
        BigDecimal maxBalance = getMaxWalletBalance(tier);

        if (amount.compareTo(singleLimit) > 0) {
            throw new IllegalArgumentException("Transfer amount exceeds single transfer limit for your tier");
        }

        BigDecimal todayDebits = transactionRepository.sumTodayDebits(wallet.getId(), LocalDate.now());
        if (todayDebits.add(amount).compareTo(dailyLimit) > 0) {
            throw new IllegalArgumentException("Transfer would exceed daily debit limit");
        }

        if (wallet.getBalance().compareTo(maxBalance) >= 0) {
            throw new IllegalArgumentException("Wallet balance exceeds allowed max for your tier");
        }
    }

    private BigDecimal getSingleTransferLimit(String tier) {
        return switch (tier) {
            case "TIER_2" -> new BigDecimal("200000.00");
            case "TIER_3" -> new BigDecimal("1000000.00");
            default -> new BigDecimal("50000.00");
        };
    }

    private BigDecimal getDailyDebitLimit(String tier) {
        return switch (tier) {
            case "TIER_2" -> new BigDecimal("500000.00");
            case "TIER_3" -> new BigDecimal("5000000.00");
            default -> new BigDecimal("300000.00");
        };
    }

    private BigDecimal getMaxWalletBalance(String tier) {
        return switch (tier) {
            case "TIER_2" -> new BigDecimal("500000.00");
            case "TIER_3" -> new BigDecimal("99999999.00");
            default -> new BigDecimal("300000.00");
        };
    }
}
