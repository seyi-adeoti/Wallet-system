package com.wallet.service;

import com.wallet.dto.response.TransactionResponse;
import com.wallet.dto.response.WalletBalanceResponse;
import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.entity.WalletTransaction;
import com.wallet.enums.TransactionStatus;
import com.wallet.enums.TransactionType;
import com.wallet.repository.WalletRepository;
import com.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    @Transactional
    public Wallet createWallet(User user) {
        Wallet wallet = Wallet.builder()
                .user(user)
                .accountNumber(generateAccountNumber())
                .currency("NGN")
                .balance(BigDecimal.ZERO)
                .ledgerBalance(BigDecimal.ZERO)
                .active(true)
                .build();
        return walletRepository.save(wallet);
    }

    @Transactional(readOnly = true)
    public Wallet getWalletByUserId(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user"));
    }

    @Transactional(readOnly = true)
    public WalletBalanceResponse getBalance(UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        return WalletBalanceResponse.builder()
                .accountNumber(wallet.getAccountNumber())
                .currency(wallet.getCurrency())
                .balance(wallet.getBalance())
                .ledgerBalance(wallet.getLedgerBalance())
                .build();
    }

    @Transactional
    public WalletTransaction creditWallet(Wallet wallet, BigDecimal amount, String reference,
                                          String description, String transactionType) {
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        wallet.setBalance(balanceAfter);
        wallet.setLedgerBalance(balanceAfter);
        walletRepository.save(wallet);

        WalletTransaction txn = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.CREDIT)
                .amount(amount)
                .fee(BigDecimal.ZERO)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .reference(reference)
                .description(description)
                .status(TransactionStatus.SUCCESS)
                .transactionType(transactionType)
                .build();

        return transactionRepository.save(txn);
    }

    @Transactional
    public WalletTransaction debitWallet(Wallet wallet, BigDecimal amount, BigDecimal fee,
                                         String reference, String description, String transactionType) {
        BigDecimal totalDeduction = amount.add(fee);
        if (wallet.getBalance().compareTo(totalDeduction) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(totalDeduction);

        wallet.setBalance(balanceAfter);
        wallet.setLedgerBalance(balanceAfter);
        walletRepository.save(wallet);

        WalletTransaction txn = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEBIT)
                .amount(amount)
                .fee(fee)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .reference(reference)
                .description(description)
                .status(TransactionStatus.SUCCESS)
                .transactionType(transactionType)
                .build();

        return transactionRepository.save(txn);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistory(UUID userId, int page, int size) {
        Wallet wallet = getWalletByUserId(userId);
        Pageable pageable = PageRequest.of(page, size);
        Page<WalletTransaction> txns = transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);
        return txns.stream()
                .map(txn -> TransactionResponse.builder()
                        .reference(txn.getReference())
                        .type(txn.getType().name())
                        .amount(txn.getAmount())
                        .fee(txn.getFee())
                        .balanceBefore(txn.getBalanceBefore())
                        .balanceAfter(txn.getBalanceAfter())
                        .status(txn.getStatus().name())
                        .transactionType(txn.getTransactionType())
                        .createdAt(txn.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal sumTodayDebits(UUID walletId) {
        return transactionRepository.sumTodayDebits(walletId, LocalDate.now());
    }

    private String generateAccountNumber() {
        String number = "20" + (System.currentTimeMillis() % 100000000L);
        while (walletRepository.existsByAccountNumber(number)) {
            number = "20" + (System.currentTimeMillis() % 100000000L);
        }
        return number;
    }
}
