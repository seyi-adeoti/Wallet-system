package com.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final WalletTransactionRepository transactionRepository;
    private final NipTransactionRepository nipRepository;
    private final AmlFlagRepository amlFlagRepository;
    private final SettlementReportRepository reportRepository;
    private final GeneralLedgerService glService;

    // Runs every night at 11:55 PM via @Scheduled
    @Scheduled(cron = "0 55 23 * * *")
    @Transactional
    public SettlementReport generateDailyReport() {
        LocalDate today = LocalDate.now();
        return generateReportForDate(today);
    }

    public SettlementReport generateReportForDate(LocalDate date) {

        log.info("Generating settlement report for {}", date);

        // Check if already generated
        if (reportRepository.existsByReportDate(date)) {
            log.warn("Settlement report already exists for {}", date);
            return reportRepository.findByReportDate(date).get();
        }

        // Aggregate from wallet_transactions
        BigDecimal totalCredits = transactionRepository
            .sumByTypeAndDateAndStatus("CREDIT", date, "SUCCESS");

        BigDecimal totalDebits = transactionRepository
            .sumByTypeAndDateAndStatus("DEBIT", date, "SUCCESS");

        BigDecimal totalFees = transactionRepository
            .sumFeesByDateAndStatus(date, "SUCCESS");

        BigDecimal totalReversals = transactionRepository
            .sumByTransactionTypeAndDateAndStatus("REVERSAL", date, "SUCCESS");

        long transactionCount = transactionRepository
            .countByDateAndStatus(date, "SUCCESS");

        // NIP-specific stats
        BigDecimal totalNipOutward = nipRepository
            .sumByStatusAndDate(NipStatus.SUCCESS, date);

        long nipCount = nipRepository.countByStatusAndDate(NipStatus.SUCCESS, date);

        // AML stats
        long amlFlagsRaised = amlFlagRepository.countByCreatedDate(date);

        // Net position
        BigDecimal netPosition = totalCredits.subtract(totalDebits);

        // Get GL trial balance to verify
        TrialBalanceResponse trialBalance = glService.getTrialBalance(date);

        SettlementReport report = SettlementReport.builder()
                .reportDate(date)
                .totalCredits(totalCredits)
                .totalDebits(totalDebits)
                .totalNipOutward(totalNipOutward)
                .totalFees(totalFees)
                .totalReversals(totalReversals)
                .transactionCount((int) transactionCount)
                .nipCount((int) nipCount)
                .amlFlagsRaised((int) amlFlagsRaised)
                .netPosition(netPosition)
                .status(trialBalance.isBalanced() ? "FINAL" : "DRAFT")
                .build();

        report = reportRepository.save(report);

        if (!trialBalance.isBalanced()) {
            log.error("CRITICAL: Trial balance out of balance for {}! Report saved as DRAFT.",
                date);
        } else {
            log.info("Settlement report FINAL for {} | Credits:{} Debits:{} Net:{}",
                date, totalCredits, totalDebits, netPosition);
        }

        return report;
    }
}