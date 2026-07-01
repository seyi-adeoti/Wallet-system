package com.wallet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for interbank transfer via NIP")
public class NipTransferResponse {

    @Schema(description = "Transfer ID", example = "TRANSFER-550e8400-e29b-41d4-a716-446655440000")
    private String transferId;

    @Schema(description = "Reference ID provided by client", example = "REF-20260701-002")
    private String referenceId;

    @Schema(description = "NIP reference number", example = "NIP-20260701-001")
    private String nipReference;

    @Schema(description = "Transfer status", example = "PENDING")
    private String status;

    @Schema(description = "Transfer amount in kobo", example = "100000")
    private Long amount;

    @Schema(description = "Transfer charges in kobo", example = "50")
    private Long charges;

    @Schema(description = "Total debit amount (amount + charges)", example = "100050")
    private Long totalDebit;

    @Schema(description = "Estimated delivery time")
    private LocalDateTime estimatedDelivery;

    @Schema(description = "NIP response code", example = "00")
    private String responseCode;

    @Schema(description = "NIP response message", example = "Success")
    private String responseMessage;
}
