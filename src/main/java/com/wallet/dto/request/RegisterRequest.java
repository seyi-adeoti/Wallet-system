package com.wallet.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User registration request")
public class RegisterRequest {
    
    @NotBlank
    @Schema(description = "User's first name", example = "John")
    private String firstName;

    @NotBlank
    @Schema(description = "User's last name", example = "Doe")
    private String lastName;

    @Email
    @NotBlank
    @Schema(description = "User's email address", example = "user@example.com")
    private String email;

    @NotBlank
    @Pattern(regexp = "^(\\+234|0)[789][01]\\d{8}$",
             message = "Enter a valid Nigerian phone number")
    @Schema(description = "Nigerian phone number", example = "08123456789")
    private String phoneNumber;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(description = "User's password", example = "SecurePass123!")
    private String password;
}

