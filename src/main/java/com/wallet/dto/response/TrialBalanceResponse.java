package com.wallet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "General Ledger trial balance response")
public class TrialBalanceResponse {

    @Schema(description = "Date of trial balance", example = "2026-07-01")
    private LocalDate date;

    @Schema(description = "List of GL accounts with balances")
    private List<GlAccountBalance> accounts;

    @Schema(description = "Total debit amount")
    private BigDecimal totalDebits;

    @Schema(description = "Total credit amount")
    private BigDecimal totalCredits;

    @Schema(description = "Is trial balance balanced", example = "true")
    private boolean balanced;

    @Schema(description = "Variance amount (if unbalanced)")
    private BigDecimal variance;

    @Schema(description = "Generated timestamp")
    private String generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GlAccountBalance {
        @Schema(description = "GL Account code", example = "1001")
        private String accountCode;

        @Schema(description = "GL Account name", example = "Customer Deposits")
        private String accountName;

        @Schema(description = "Account type", example = "ASSET")
        private String accountType;

        @Schema(description = "Debit balance")
        private BigDecimal debit;

        @Schema(description = "Credit balance")
        private BigDecimal credit;

        @Schema(description = "Net balance")
        private BigDecimal balance;

        @Schema(description = "Is account active", example = "true")
        private boolean isActive;
    }
}
