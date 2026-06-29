package com.wallet.service;

import com.wallet.entity.AmlFlag;
import com.wallet.enums.AmlFlagType;
import com.wallet.repository.AmlFlagRepository;
import com.wallet.repository.WalletTransactionRepository;
import com.wallet.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmlService {

    private final AmlFlagRepository amlFlagRepository;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    // CBN thresholds
    private static final BigDecimal LARGE_TRANSACTION_THRESHOLD = new BigDecimal("5000000");  // ₦5m
    private static final BigDecimal STRUCTURING_THRESHOLD       = new BigDecimal("4900000");  // just under ₦5m
    private static final int HIGH_FREQUENCY_COUNT               = 20;  // 20 transactions in 1 hour
    private static final BigDecimal HIGH_FREQUENCY_AMOUNT       = new BigDecimal("1000000");  // ₦1m in 1 hour

    // Called after every transaction — non-blocking
    @Async
    public void screenTransaction(UUID userId, UUID walletId,
                                   BigDecimal amount, String reference,
                                   String transactionType) {

        log.debug("AML screening → ref: {} amount: {}", reference, amount);

        // Rule 1: Large transaction (CBN CTR requirement — must report above ₦5m)
        if (amount.compareTo(LARGE_TRANSACTION_THRESHOLD) >= 0) {
            raiseFlag(
                userId, walletId, reference, amount,
                AmlFlagType.LARGE_TRANSACTION,
                String.format("Transaction of ₦%s exceeds CBN CTR threshold of ₦5,000,000", amount)
            );
        }

        // Rule 2: Structuring — amounts just under ₦5m (suspicious pattern)
        if (amount.compareTo(STRUCTURING_THRESHOLD) >= 0 &&
            amount.compareTo(LARGE_TRANSACTION_THRESHOLD) < 0) {
            raiseFlag(
                userId, walletId, reference, amount,
                AmlFlagType.STRUCTURING,
                String.format("Amount ₦%s appears to be structured to avoid ₦5m CTR threshold", amount)
            );
        }

        // Rule 3: High frequency — too many transactions in 1 hour
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentCount = transactionRepository
            .countByWalletIdAndCreatedAtAfter(walletId, oneHourAgo);

        if (recentCount > HIGH_FREQUENCY_COUNT) {
            raiseFlag(
                userId, walletId, reference, amount,
                AmlFlagType.HIGH_FREQUENCY,
                String.format("%d transactions in the last hour", recentCount)
            );
        }

        // Rule 4: High volume — total amount moved in 1 hour
        BigDecimal hourlyVolume = transactionRepository
            .sumDebitsByWalletSince(walletId, oneHourAgo);

        if (hourlyVolume != null &&
            hourlyVolume.compareTo(HIGH_FREQUENCY_AMOUNT) >= 0) {
            raiseFlag(
                userId, walletId, reference, hourlyVolume,
                AmlFlagType.UNUSUAL_PATTERN,
                String.format("₦%s moved in last hour — unusual velocity", hourlyVolume)
            );
        }
    }

    private void raiseFlag(UUID userId, UUID walletId, String ref,
                            BigDecimal amount, AmlFlagType type, String description) {

        // Don't duplicate flags for same transaction + same type
        if (amlFlagRepository.existsByTransactionRefAndFlagType(ref, type)) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);

        AmlFlag flag = AmlFlag.builder()
                .user(user)
                .walletId(walletId)
                .transactionRef(ref)
                .amount(amount)
                .flagType(type)
                .description(description)
                .status("OPENED")
                .build();

        amlFlagRepository.save(flag);

        log.warn("AML FLAG RAISED → Type:{} UserId:{} Ref:{} Amount:{}",
            type, userId, ref, amount);
    }

    // For compliance officers to review flags
    public Page<AmlFlag> getOpenFlags(Pageable pageable) {
        return amlFlagRepository.findByStatus("OPENED", pageable);
    }

    public AmlFlag reviewFlag(UUID flagId, String decision,
                               String reviewedBy, String notes) {
        AmlFlag flag = amlFlagRepository.findById(flagId)
                .orElseThrow(() -> new WalletException("Flag not found"));

        flag.setStatus(decision.equalsIgnoreCase("CLEARED") ? "CLOSED" : "REOPENED");
        flag.setReviewedBy(reviewedBy);
        flag.setReviewedAt(LocalDateTime.now());
        flag.setDescription(flag.getDescription() + " | Review: " + notes);

        return amlFlagRepository.save(flag);
    }
}