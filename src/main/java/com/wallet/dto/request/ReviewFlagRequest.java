package com.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AML flag review request")
public class ReviewFlagRequest {

    @NotBlank
    @Schema(description = "Review decision", example = "APPROVED", allowableValues = {"APPROVED", "REJECTED", "ESCALATED"})
    private String decision;

    @NotBlank
    @Schema(description = "Reviewer email", example = "admin@example.com")
    private String reviewedBy;

    @NotBlank
    @Schema(description = "Review notes", example = "Transaction verified as legitimate business payment")
    private String notes;
}
