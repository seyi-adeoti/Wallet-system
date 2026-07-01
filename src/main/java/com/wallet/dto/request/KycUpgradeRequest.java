package com.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "KYC upgrade request")
public class KycUpgradeRequest {

    @NotBlank
    @Pattern(regexp = "^[0-9]{11}$", message = "BVN must be 11 digits")
    @Schema(description = "Bank Verification Number (11 digits)", example = "12345678901")
    private String bvn;

    @Schema(description = "National Identification Number (14 digits)", example = "12345678901234")
    @Pattern(regexp = "^[0-9]{14}$", message = "NIN must be 14 digits")
    private String nin;

    @NotBlank
    @Schema(description = "Residential address", example = "123 Test Street, Lagos")
    private String address;

    @NotBlank
    @Schema(description = "City", example = "Lagos")
    private String city;

    @NotBlank
    @Schema(description = "State", example = "Lagos State")
    private String state;

    @NotBlank
    @Schema(description = "Zip/Postal code", example = "100001")
    private String zipCode;

    @Schema(description = "Country code", example = "NG")
    private String country;
}
