package com.wallet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Account name enquiry response from NIP")
public class NameEnquiryResponse {

    @Schema(description = "Account number", example = "1234567890")
    private String accountNumber;

    @Schema(description = "Bank code", example = "001")
    private String bankCode;

    @Schema(description = "Account holder name", example = "John Doe")
    private String accountName;

    @Schema(description = "Bank name", example = "Example Bank")
    private String bankName;

    @Schema(description = "Account status", example = "true")
    private boolean isActive;

    @Schema(description = "Response code from NIP", example = "00")
    private String responseCode;

    @Schema(description = "Response message", example = "Successful")
    private String responseMessage;
}
