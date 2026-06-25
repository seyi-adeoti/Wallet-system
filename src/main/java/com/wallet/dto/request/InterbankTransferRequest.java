package com.wallet.dto.request;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;


@Data
public class InterbankTransferRequest {
    @NotBlank
    private String receiverAccount;

    @NotBlank
    private String bankCode;

    @NotBlank
    private String receiverName;

    @NotNull @DecimalMin("100.00")
    private BigDecimal amount;

    private String narration;

    @NotBlank
    private String reference;
}