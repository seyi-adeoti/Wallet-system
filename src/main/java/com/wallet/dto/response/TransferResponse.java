package com.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransferResponse {
    private String reference;
    private BigDecimal amount;
    private String status;
    private String receiverAccountNumber;
    private LocalDateTime timestamp;
}
