package com.wallet.util;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.Random;

// In production this becomes an HTTP client calling real NIBSS APIs
@Component
@Slf4j
public class NibssGatewaySimulator {

    private final Random random = new Random();

    // Nigerian bank codes
    private static final Map<String, String> BANK_CODES = Map.of(
        "044", "Access Bank",
        "058", "GTBank",
        "011", "First Bank",
        "033", "UBA",
        "057", "Zenith Bank",
        "070", "Fidelity Bank",
        "000013", "GTBank (MFB)",
        "50515", "Moniepoint MFB"
    );

    // Name Enquiry — verify account before sending
    public NameEnquiryResponse nameEnquiry(String accountNumber, String bankCode) {
        log.info("NIP Name Enquiry → Account: {} Bank: {}", accountNumber, bankCode);

        // Simulate network delay (50-300ms)
        simulateDelay(50, 300);

        // Simulate occasional failures (5% chance)
        if (random.nextInt(100) < 5) {
            return NameEnquiryResponse.builder()
                    .responseCode("96")
                    .responseMessage("System malfunction")
                    .successful(false)
                    .build();
        }

        // Simulate account not found (10% chance)
        if (random.nextInt(100) < 10) {
            return NameEnquiryResponse.builder()
                    .responseCode("25")
                    .responseMessage("Account not found")
                    .successful(false)
                    .build();
        }

        // Success — return simulated account name
        String bankName = BANK_CODES.getOrDefault(bankCode, "Unknown Bank");
        return NameEnquiryResponse.builder()
                .accountNumber(accountNumber)
                .accountName(generateFakeName())   // in prod: real name from dest bank
                .bankCode(bankCode)
                .bankName(bankName)
                .sessionId(generateSessionId())
                .responseCode("00")
                .responseMessage("Approved")
                .successful(true)
                .build();
    }

    // Funds Transfer — the actual send
    public NipTransferResponse sendFunds(NipTransferRequest request) {
        log.info("NIP Transfer → {} to {}/{} Amount: {}",
            request.getSenderAccount(),
            request.getReceiverAccount(),
            request.getBankCode(),
            request.getAmount());

        simulateDelay(200, 800); // real NIP is usually 200ms-2s

        int outcome = random.nextInt(100);

        // 80% success
        if (outcome < 80) {
            return NipTransferResponse.builder()
                    .sessionId(request.getSessionId())
                    .responseCode("00")
                    .responseMessage("Approved by Financial Institution")
                    .successful(true)
                    .build();
        }

        // 10% timeout (the worst case — don't know if money moved)
        if (outcome < 90) {
            simulateDelay(28000, 32000); // simulate 28-32 second timeout
            return NipTransferResponse.builder()
                    .sessionId(request.getSessionId())
                    .responseCode("TO")
                    .responseMessage("Transaction timed out")
                    .successful(false)
                    .timeout(true)
                    .build();
        }

        // 10% failure (definite failure — money did NOT move)
        String[] failures = {"51", "61", "65"};
        String[] messages = {"Insufficient funds at dest", "Exceeds withdrawal limit", "Exceeds frequency limit"};
        int fi = random.nextInt(3);

        return NipTransferResponse.builder()
                .sessionId(request.getSessionId())
                .responseCode(failures[fi])
                .responseMessage(messages[fi])
                .successful(false)
                .timeout(false)
                .build();
    }

    // Status Enquiry — called after timeout
    public NipStatusResponse queryTransactionStatus(String sessionId) {
        log.info("NIP Status Enquiry → SessionId: {}", sessionId);
        simulateDelay(100, 500);

        // 60% — transaction actually went through despite timeout
        if (random.nextInt(100) < 60) {
            return NipStatusResponse.builder()
                    .sessionId(sessionId)
                    .responseCode("00")
                    .responseMessage("Approved")
                    .successful(true)
                    .build();
        }

        // 40% — transaction truly failed
        return NipStatusResponse.builder()
                .sessionId(sessionId)
                .responseCode("96")
                .responseMessage("Transaction not found / failed")
                .successful(false)
                .build();
    }

    private void simulateDelay(int minMs, int maxMs) {
        try {
            Thread.sleep(minMs + random.nextInt(maxMs - minMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String generateSessionId() {
        return "NIBSS" + System.currentTimeMillis() + random.nextInt(9999);
    }

    private String generateFakeName() {
        String[] names = {
            "ADEBAYO MICHAEL OLUWASEUN",
            "CHISOM GRACE OKONKWO",
            "IBRAHIM MUSA ABDULLAHI",
            "FUNMILAYO SARAH ADEYEMI",
            "EMEKA JOHN NWOSU"
        };
        return names[random.nextInt(names.length)];
    }
}