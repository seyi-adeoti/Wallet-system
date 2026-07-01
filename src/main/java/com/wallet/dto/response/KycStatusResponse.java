package com.wallet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "KYC status response with current tier and limits")
public class KycStatusResponse {

    @Schema(description = "Current KYC tier", example = "TIER_1")
    private String currentTier;

    @Schema(description = "Single transaction limit in kobo", example = "50000")
    private Long singleTransactionLimit;

    @Schema(description = "Daily transaction limit in kobo", example = "300000")
    private Long dailyLimit;

    @Schema(description = "Monthly transaction limit in kobo", example = "1000000")
    private Long monthlyLimit;

    @Schema(description = "Next tier available for upgrade", example = "TIER_2")
    private String nextTier;

    @Schema(description = "Amount used today in kobo", example = "150000")
    private Long dailyUsed;

    @Schema(description = "Amount used this month in kobo", example = "500000")
    private Long monthlyUsed;

    @Schema(description = "Can user upgrade to next tier", example = "true")
    private boolean canUpgrade;

    @Schema(description = "Requirements to upgrade to next tier")
    private List<String> upgradeRequirements;

    @Schema(description = "KYC verification status")
    private String verificationStatus;

    @Schema(description = "Verification timestamp")
    private String verifiedAt;
}
