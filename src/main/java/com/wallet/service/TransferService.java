package com.wallet.service;

import com.wallet.dto.request.WalletTransferRequest;
import com.wallet.dto.response.TransferResponse;
import com.wallet.entity.Transfer;
import com.wallet.entity.Wallet;
import com.wallet.entity.WalletTransaction;
import com.wallet.enums.TransactionStatus;
import com.wallet.repository.TransferRepository;
import com.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Orchestrates wallet-to-wallet transfers: validation, locking, idempotency, and coordination
 * of debit/credit ledger entries via {@link WalletService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final WalletService walletService;
    private final KycLimitService kycLimitService;
    private final EntityManager entityManager;

    /**
     * Transfers funds from the sender's wallet to a receiver account.
     * Idempotent by reference — duplicate references return the existing transfer.
     * Uses pessimistic row locks and a single DB transaction so debit + credit are atomic.
     */
    @Transactional
    public TransferResponse walletToWallet(UUID senderUserId, WalletTransferRequest request) {
        log.info("Transfer request: senderUserId={} ref={} amount={} receiver={}",
                senderUserId, request.getReference(), request.getAmount(), request.getReceiverAccountNumber());

        var existing = transferRepository.findByReference(request.getReference());
        if (existing.isPresent()) {
            log.info("Idempotent hit — returning existing transfer ref={}", request.getReference());
            return mapToResponse(existing.get());
        }

        Wallet senderWallet = walletRepository.findByUserId(senderUserId)
                .orElseThrow(() -> new IllegalArgumentException("Sender wallet not found"));

        Wallet receiverWallet = walletRepository.findByAccountNumber(request.getReceiverAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Receiver account not found"));

        if (!senderWallet.isActive()) {
            throw new IllegalArgumentException("Sender wallet is inactive");
        }
        if (!receiverWallet.isActive()) {
            throw new IllegalArgumentException("Receiver wallet is inactive");
        }
        if (senderWallet.getId().equals(receiverWallet.getId())) {
            throw new IllegalArgumentException("Cannot transfer to yourself");
        }

        kycLimitService.validateTransferLimit(senderWallet, request.getAmount());

        BigDecimal fee = calculateFee(request.getAmount());
        BigDecimal totalDeduction = request.getAmount().add(fee);
        log.debug("Transfer fee={} totalDeduction={}", fee, totalDeduction);

        UUID firstLockId = senderWallet.getId().compareTo(receiverWallet.getId()) < 0
                ? senderWallet.getId() : receiverWallet.getId();
        UUID secondLockId = firstLockId.equals(senderWallet.getId())
                ? receiverWallet.getId() : senderWallet.getId();

        log.debug("Acquiring pessimistic locks on wallets {} then {}", firstLockId, secondLockId);
        Wallet lockedFirst = walletRepository.lockById(firstLockId).orElseThrow();
        Wallet lockedSecond = walletRepository.lockById(secondLockId).orElseThrow();

        Wallet lockedSender = firstLockId.equals(senderWallet.getId()) ? lockedFirst : lockedSecond;
        Wallet lockedReceiver = firstLockId.equals(receiverWallet.getId()) ? lockedFirst : lockedSecond;
        entityManager.refresh(lockedSender);
        entityManager.refresh(lockedReceiver);

        if (lockedSender.getBalance().compareTo(totalDeduction) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        Transfer transfer = transferRepository.save(Transfer.builder()
                .senderWalletId(lockedSender.getId())
                .receiverWalletId(lockedReceiver.getId())
                .amount(request.getAmount())
                .fee(fee)
                .reference(request.getReference())
                .narration(request.getNarration())
                .status(TransactionStatus.PENDING)
                .build());

        try {
            WalletTransaction debitTxn = walletService.debitWallet(
                    lockedSender,
                    request.getAmount(),
                    fee,
                    request.getReference() + "-DR",
                    "Transfer to " + lockedReceiver.getAccountNumber(),
                    "WALLET_TRANSFER"
            );

            WalletTransaction creditTxn = walletService.creditWallet(
                    lockedReceiver,
                    request.getAmount(),
                    request.getReference() + "-CR",
                    "Transfer from " + lockedSender.getAccountNumber(),
                    "WALLET_TRANSFER"
            );

            transfer.setStatus(TransactionStatus.SUCCESS);
            transfer.setSenderTransactionId(debitTxn.getId());
            transfer.setReceiverTransactionId(creditTxn.getId());
            transferRepository.save(transfer);

            log.info("Transfer successful: {}", request.getReference());
            return mapToResponse(transfer);
        } catch (Exception e) {
            transfer.setStatus(TransactionStatus.FAILED);
            transferRepository.save(transfer);
            log.error("Transfer failed: {}", request.getReference(), e);
            throw new IllegalStateException("Transfer failed", e);
        }
    }

    /** Maps a {@link Transfer} entity to the API response DTO. */
    private TransferResponse mapToResponse(Transfer transfer) {
        return TransferResponse.builder()
                .reference(transfer.getReference())
                .amount(transfer.getAmount())
                .status(transfer.getStatus().name())
                .receiverAccountNumber(transfer.getReceiverWalletId().toString())
                .timestamp(transfer.getCreatedAt())
                .build();
    }

    /** Computes transfer fee; currently zero (placeholder for future pricing). */
    private BigDecimal calculateFee(BigDecimal amount) {
        return BigDecimal.ZERO;
    }
}
