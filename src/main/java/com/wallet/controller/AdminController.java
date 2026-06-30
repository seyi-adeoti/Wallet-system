package com.wallet.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import com.wallet.service.*;



@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AmlService amlService;
    private final SettlementService settlementService;
    private final GeneralLedgerService glService;

    // AML
    @GetMapping("/aml/flags")
    public ResponseEntity<Page<AmlFlag>> getOpenFlags(Pageable pageable) {
        return ResponseEntity.ok(amlService.getOpenFlags(pageable));
    }

    @PatchMapping("/aml/flags/{flagId}/review")
    public ResponseEntity<AmlFlag> reviewFlag(
            @PathVariable UUID flagId,
            @RequestBody ReviewFlagRequest request) {
        return ResponseEntity.ok(
            amlService.reviewFlag(flagId, request.getDecision(),
                request.getReviewedBy(), request.getNotes())
        );
    }

    // Settlement
    @GetMapping("/settlement/{date}")
    public ResponseEntity<SettlementReport> getSettlementReport(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(settlementService.generateReportForDate(date));
    }

    // GL
    @GetMapping("/gl/trial-balance")
    public ResponseEntity<TrialBalanceResponse> getTrialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(glService.getTrialBalance(date));
    }

    @GetMapping("/gl/account/{code}/balance")
    public ResponseEntity<Map<String, Object>> getGlAccountBalance(
            @PathVariable String code) {
        BigDecimal balance = glService.getAccountBalance(code);
        return ResponseEntity.ok(Map.of("accountCode", code, "balance", balance));
    }
}

