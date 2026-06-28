package com.wallet.service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import com.wallet.entity.NipTransaction;
import com.wallet.entity.Wallet;
import com.wallet.enums.NipStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class NipService {

    private final NibssGatewaySimulator nibssGateway;
    private final NipTransactionRepository nipRepository;
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final WalletCacheService walletCacheService;
    private final RateLimiterService rateLimiterService;
    private final GeneralLedgerService generalLedgerService;

    private static final BigDecimal NIP_FEE = new BigDecimal("50.00"); // ₦50 per transfer

    // ── STEP A: Name Enquiry ─────────────────────────────────────────
    public NameEnquiryResponse nameEnquiry(String accountNumber, String bankCode) {
        NameEnquiryResponse response = nibssGateway.nameEnquiry(accountNumber, bankCode);

        if (!response.isSuccessful()) {
            throw new WalletException("Could not verify account: " + response.getResponseMessage());
        }

        return response;
    }

    // ── STEP B: Initiate Interbank Transfer ──────────────────────────
    @Transactional
    public NipTransferResponse initiateTransfer(UUID senderUserId,
                                                 InterbankTransferRequest request) {

        // Rate limit check
        if (!rateLimiterService.isTransferAllowed(senderUserId)) {
            log.error("Too many transfers. Please wait a minute.");
            throw new WalletException("Too many transfers. Please wait a minute.");
        }

        // Idempotency check
        if (nipRepository.existsByReference(request.getReference())) {
            NipTransaction existing = nipRepository.findByReference(request.getReference()).get();
            return buildResponse(existing);
        }

        // Load sender wallet
        Wallet senderWallet = walletRepository.findByUserId(senderUserId)
                .orElseThrow(() -> new WalletException("Wallet not found"));

        // KYC tier limit check (same as intra-wallet)
        BigDecimal totalAmount = request.getAmount().add(NIP_FEE);
        if (senderWallet.getBalance().compareTo(totalAmount) < 0) {
            throw new WalletException("Insufficient funds. Amount + ₦50 fee required.");
        }

        // Generate NIBSS session ID
        String sessionId = "NIP" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        // Create NIP transaction record (PENDING)
        NipTransaction nipTxn = NipTransaction.builder()
                .senderWalletId(senderWallet.getId())
                .senderAccount(senderWallet.getAccountNumber())
                .receiverAccount(request.getReceiverAccount())
                .receiverBankCode(request.getBankCode())
                .receiverName(request.getReceiverName())
                .amount(request.getAmount())
                .fee(NIP_FEE)
                .reference(request.getReference())
                .sessionId(sessionId)
                .narration(request.getNarration())
                .status(NipStatus.PENDING)
                .build();

        nipTxn = nipRepository.save(nipTxn);

        // DEBIT sender wallet BEFORE sending to NIBSS
        // (money is held — if NIBSS fails, we reverse)
        walletService.debitWallet(
            senderWallet,
            request.getAmount(),
            NIP_FEE,
            request.getReference() + "-NIP-DR",
            "Interbank transfer to " + request.getReceiverAccount() + "/" + request.getBankCode(),
            "NIP_TRANSFER"
        );

        generalLedgerService.postNipOutwardTransfer(
    request.getAmount(),
    NIP_FEE,
    request.getReference(),
    senderWallet.getAccountNumber()
);

        // Update status to PROCESSING
        nipTxn.setStatus(NipStatus.PROCESSING);
        nipTxn.setNibssSentAt(LocalDateTime.now());
        nipRepository.save(nipTxn);

        // Send to NIBSS
        NipTransferRequest nibssRequest = NipTransferRequest.builder()
                .sessionId(sessionId)
                .senderAccount(senderWallet.getAccountNumber())
                .receiverAccount(request.getReceiverAccount())
                .bankCode(request.getBankCode())
                .amount(request.getAmount())
                .narration(request.getNarration())
                .build();

        NipTransferResponse nibssResponse = nibssGateway.sendFunds(nibssRequest);
        nipTxn.setNibssRespondedAt(LocalDateTime.now());

        // ── Handle NIBSS response ─────────────────────────────────

        if (nibssResponse.isSuccessful()) {
            // Clean success
            nipTxn.setStatus(NipStatus.SUCCESS);
            nipTxn.setResponseCode("00");
            nipTxn.setResponseMessage("Transfer successful");
            nipRepository.save(nipTxn);

            log.info("NIP Transfer SUCCESS. Ref: {} SessionId: {}",
                request.getReference(), sessionId);

        } else if (nibssResponse.isTimeout()) {
            // TIMEOUT — most dangerous case. Don't know if money moved.
            // Mark as TIMEOUT and let status enquiry job resolve it
            nipTxn.setStatus(NipStatus.TIMEOUT);
            nipTxn.setResponseCode("TO");
            nipTxn.setResponseMessage("Transaction timed out — status pending verification");
            nipRepository.save(nipTxn);

            log.warn("NIP Transfer TIMEOUT. Ref: {} — will query status", request.getReference());

            // Trigger async status enquiry
            resolveTimeout(nipTxn);

        } else {
            // Definite failure — reverse the debit
            nipTxn.setStatus(NipStatus.FAILED);
            nipTxn.setResponseCode(nibssResponse.getResponseCode());
            nipTxn.setResponseMessage(nibssResponse.getResponseMessage());
            nipRepository.save(nipTxn);

            // Reverse debit
            reverseDebit(senderWallet, request.getAmount(), NIP_FEE, request.getReference());

            generalLedgerService.postNipInwardTransfer(
    request.getAmount(),
    NIP_FEE,
    request.getReference(),
    senderWallet.getAccountNumber()
);

            log.warn("NIP Transfer FAILED. Ref: {} Code: {} - Reversed",
                request.getReference(), nibssResponse.getResponseCode());
        }

        return buildResponse(nipTxn);
    }

    // ── STEP C: Resolve Timeout ──────────────────────────────────────
    // In prod this would be async / scheduled. Here we call it inline.
    private void resolveTimeout(NipTransaction nipTxn) {
        log.info("Resolving timeout for SessionId: {}", nipTxn.getSessionId());

        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;
            log.info("Status enquiry attempt {}/{} for ref: {}",
                attempt, maxRetries, nipTxn.getReference());

            try {
                Thread.sleep(2000L * attempt); // backoff: 2s, 4s, 6s
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            NipStatusResponse statusResponse =
                nibssGateway.queryTransactionStatus(nipTxn.getSessionId());

            if (statusResponse.isSuccessful()) {
                // Transaction went through — mark success, keep debit
                nipTxn.setStatus(NipStatus.SUCCESS);
                nipTxn.setResponseCode("00");
                nipTxn.setResponseMessage("Confirmed successful via status enquiry");
                nipTxn.setRetryCount(attempt);
                nipRepository.save(nipTxn);
                log.info("Timeout resolved → SUCCESS. Ref: {}", nipTxn.getReference());
                return;
            }

            // If last attempt and still not confirmed → reverse
            if (attempt == maxRetries) {
                Wallet senderWallet = walletRepository
                    .findById(nipTxn.getSenderWalletId()).orElseThrow();

                reverseDebit(senderWallet, nipTxn.getAmount(),
                             nipTxn.getFee(), nipTxn.getReference());

                nipTxn.setStatus(NipStatus.REVERSED);
                nipTxn.setResponseCode("96");
                nipTxn.setResponseMessage("Reversed after failed status enquiry");
                nipTxn.setRetryCount(attempt);
                nipRepository.save(nipTxn);
                log.warn("Timeout unresolved → REVERSED. Ref: {}", nipTxn.getReference());
            }
        }
    }

    // ── Reversal Logic ───────────────────────────────────────────────
    private void reverseDebit(Wallet wallet, BigDecimal amount,
                               BigDecimal fee, String originalRef) {
        BigDecimal totalRefund = amount.add(fee);

        walletService.creditWallet(
            wallet,
            totalRefund,
            originalRef + "-REV",
            "Reversal: failed interbank transfer",
            "REVERSAL"
        );

        log.info("Reversed ₦{} to wallet: {}", totalRefund, wallet.getAccountNumber());
    }

    private NipTransferResponse buildResponse(NipTransaction txn) {
        return NipTransferResponse.builder()
                .reference(txn.getReference())
                .sessionId(txn.getSessionId())
                .status(txn.getStatus().name())
                .responseCode(txn.getResponseCode())
                .responseMessage(txn.getResponseMessage())
                .amount(txn.getAmount())
                .fee(txn.getFee())
                .build();
    }
}