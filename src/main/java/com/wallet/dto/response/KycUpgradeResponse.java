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
@Schema(description = "Response for KYC tier upgrade request")
public class KycUpgradeResponse {

    @Schema(description = "Request ID", example = "KYC-550e8400-e29b-41d4-a716-446655440000")
    private String requestId;

    @Schema(description = "Current tier", example = "TIER_1")
    private String currentTier;

    @Schema(description = "Requested tier", example = "TIER_2")
    private String requestedTier;

    @Schema(description = "Request status", example = "PENDING_REVIEW")
    private String status;

    @Schema(description = "Submission timestamp")
    private LocalDateTime submittedAt;

    @Schema(description = "Estimated review time", example = "1-3 business days")
    private String estimatedReviewTime;

    @Schema(description = "Message to user")
    private String message;

    @Schema(description = "Reference number for tracking")
    private String referenceNumber;
}
