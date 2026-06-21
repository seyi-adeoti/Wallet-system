package com.wallet.service;

import com.wallet.dto.request.WalletTransferRequest;
import com.wallet.dto.response.TransferResponse;
import com.wallet.entity.Transfer;
import com.wallet.entity.Wallet;
import com.wallet.entity.WalletTransaction;
import com.wallet.enums.TransactionStatus;
import com.wallet.repository.TransferRepository;
import com.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final WalletService walletService;
    private final KycLimitService kycLimitService;

    @Transactional
    public TransferResponse walletToWallet(UUID senderUserId, WalletTransferRequest request) {
        var existing = transferRepository.findByReference(request.getReference());
        if (existing.isPresent()) {
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

        if (senderWallet.getBalance().compareTo(totalDeduction) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        walletRepository.lockById(senderWallet.getId()).orElseThrow();
        walletRepository.lockById(receiverWallet.getId()).orElseThrow();

        Transfer transfer = transferRepository.save(Transfer.builder()
                .senderWalletId(senderWallet.getId())
                .receiverWalletId(receiverWallet.getId())
                .amount(request.getAmount())
                .fee(fee)
                .reference(request.getReference())
                .narration(request.getNarration())
                .status(TransactionStatus.PENDING)
                .build());

        try {
            WalletTransaction debitTxn = walletService.debitWallet(
                    senderWallet,
                    request.getAmount(),
                    fee,
                    request.getReference() + "-DR",
                    "Transfer to " + receiverWallet.getAccountNumber(),
                    "WALLET_TRANSFER"
            );

            WalletTransaction creditTxn = walletService.creditWallet(
                    receiverWallet,
                    request.getAmount(),
                    request.getReference() + "-CR",
                    "Transfer from " + senderWallet.getAccountNumber(),
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

    private TransferResponse mapToResponse(Transfer transfer) {
        return TransferResponse.builder()
                .reference(transfer.getReference())
                .amount(transfer.getAmount())
                .status(transfer.getStatus().name())
                .receiverAccountNumber(transfer.getReceiverWalletId().toString())
                .timestamp(transfer.getCreatedAt())
                .build();
    }

    private BigDecimal calculateFee(BigDecimal amount) {
        return BigDecimal.ZERO;
    }
}
