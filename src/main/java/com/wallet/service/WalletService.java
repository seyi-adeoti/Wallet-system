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

/**
 * Handles wallet lifecycle and ledger entries (credit/debit).
 * Each money movement writes a {@link WalletTransaction} with balance snapshots.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    /**
     * Creates a new NGN wallet for the given user with zero balance.
     * Called automatically during user registration (one wallet per user).
     */
    @Transactional
    public Wallet createWallet(User user) {
        log.info("Creating wallet for userId={}", user.getId());
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

    /** Loads the wallet belonging to a user; throws if none exists. */
    @Transactional(readOnly = true)
    public Wallet getWalletByUserId(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
        log.info("Wallet found for userId={}", userId);
        if (wallet == null) {
            log.error("Wallet not found for userId={}", userId);
            throw new IllegalArgumentException("Wallet not found for user");
        }
        return wallet;
    }

    /** Returns the current available and ledger balance for a user's wallet. */
    @Transactional(readOnly = true)
    public WalletBalanceResponse getBalance(UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        log.debug("Balance for userId={}: balance={}, ledger={}", userId, wallet.getBalance(), wallet.getLedgerBalance());



        // Try cache first
    Optional<BigDecimal> cached = walletCacheService.getCachedBalance(wallet.getId());
    BigDecimal balance = cached.orElseGet(() -> {
        BigDecimal fresh = wallet.getBalance();
        walletCacheService.cacheBalance(wallet.getId(), fresh); 
        return fresh;
    });

        return WalletBalanceResponse.builder()
                .accountNumber(wallet.getAccountNumber())
                .currency(wallet.getCurrency())
                .balance(wallet.getBalance())
                .ledgerBalance(wallet.getLedgerBalance())
                 .cached(cached.isPresent())
                .build();
    }

    /**
     * Credits a wallet and persists an audit entry with balanceBefore/balanceAfter snapshots.
     * Used by {@link TransferService} for the receiver leg of a transfer.
     */
    @Transactional
    public WalletTransaction creditWallet(Wallet wallet, BigDecimal amount, String reference,
                                          String description, String transactionType) {
        log.info("Credit walletId={} amount={} ref={}", wallet.getId(), amount, reference);
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

 walletCacheService.invalidateBalance(wallet.getId());
        WalletTransaction savedTxn = transactionRepository.save(txn);
        walletCacheService.cacheBalance(wallet.getId(), balanceAfter);
        return savedTxn;
    }

    /**
     * Debits a wallet (amount + fee) and persists an audit entry with balance snapshots.
     * Throws if available balance is insufficient.
     */
    @Transactional
    public WalletTransaction debitWallet(Wallet wallet, BigDecimal amount, BigDecimal fee,
                                         String reference, String description, String transactionType) {
        log.info("Debit walletId={} amount={} fee={} ref={}", wallet.getId(), amount, fee, reference);
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

       walletCacheService.invalidateBalance(wallet.getId());
        WalletTransaction savedTxn = transactionRepository.save(txn);
        walletCacheService.cacheBalance(wallet.getId(), balanceAfter);
        return savedTxn;
    }

    /** Returns paginated transaction history for a user's wallet, newest first. */
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

    /** Sums successful debit amounts (including fees) for today — used for daily KYC limits. */
    @Transactional(readOnly = true)
    public BigDecimal sumTodayDebits(UUID walletId) {
        return transactionRepository.sumTodayDebits(walletId, LocalDate.now());
    }

    /** Generates a unique 10-digit NGN account number prefixed with "20". */
    private String generateAccountNumber() {
        String number = "20" + (System.currentTimeMillis() % 100000000L);
        while (walletRepository.existsByAccountNumber(number)) {
            number = "20" + (System.currentTimeMillis() % 100000000L);
        }
        return number;
    }
}
