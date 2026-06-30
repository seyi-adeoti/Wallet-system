package com.wallet.controller;




@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "04. KYC", description = "KYC tier status and upgrade")
@SecurityRequirement(name = "Bearer Authentication")
public class KycController {

    @Operation(
        summary = "Get KYC status and limits",
        description = "Returns current tier, transaction limits, and what's needed to upgrade."
    )
    @GetMapping("/status")
    public ResponseEntity<KycStatusResponse> getStatus(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(kycService.getKycStatus(userId));
    }

    @Operation(
        summary = "Request KYC tier upgrade",
        description = """
            Submit documents to upgrade your KYC tier.
            
            **Tier requirements:**
            
            | Tier | Required | Single Limit | Daily Limit |
            |------|----------|--------------|-------------|
            | Tier 1 | Phone only | ₦50,000 | ₦300,000 |
            | Tier 2 | BVN | ₦200,000 | ₦500,000 |
            | Tier 3 | NIN + Address | ₦1,000,000 | ₦5,000,000 |
            
            You must upgrade one tier at a time (1→2→3).
            """
    )
    @PostMapping("/upgrade")
    public ResponseEntity<KycUpgradeResponse> requestUpgrade(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody KycUpgradeRequest request) {
        return ResponseEntity.ok(kycService.requestUpgrade(userId, request));
    }
}