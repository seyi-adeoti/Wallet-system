package com.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {
    private String reference;
    private String type;
    private BigDecimal amount;
    private BigDecimal fee;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String status;
    private String transactionType;
    private LocalDateTime createdAt;
}
