package com.wallet.controller;

import com.wallet.dto.request.WalletTransferRequest;
import com.wallet.dto.response.ApiResponse;
import com.wallet.dto.response.TransactionResponse;
import com.wallet.dto.response.TransferResponse;
import com.wallet.dto.response.WalletBalanceResponse;
import com.wallet.service.TransferService;
import com.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet")
public class WalletController {

    private final WalletService walletService;
    private final TransferService transferService;

    @GetMapping("/balance")
    @Operation(summary = "Get wallet balance")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getBalance(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.<WalletBalanceResponse>builder()
                .success(true)
                .message("Balance fetched successfully")
                .data(walletService.getBalance(userId))
                .build());
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds between wallets")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            Principal principal,
            @Valid @RequestBody WalletTransferRequest request) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.<TransferResponse>builder()
                .success(true)
                .message("Transfer processed")
                .data(transferService.walletToWallet(userId, request))
                .build());
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get wallet transaction history")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactions(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.<List<TransactionResponse>>builder()
                .success(true)
                .message("Transactions fetched successfully")
                .data(walletService.getTransactionHistory(userId, page, size))
                .build());
    }
}
