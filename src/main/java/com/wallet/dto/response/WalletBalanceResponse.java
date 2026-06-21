package com.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WalletBalanceResponse {
    private String accountNumber;
    private String currency;
    private BigDecimal balance;
    private BigDecimal ledgerBalance;
}
