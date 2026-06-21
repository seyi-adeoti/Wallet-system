package com.wallet.controller;

import com.wallet.dto.request.RegisterUserRequest;
import com.wallet.dto.response.ApiResponse;
import com.wallet.entity.User;
import com.wallet.repository.UserRepository;
import com.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    @Operation(summary = "Register a new user and auto-create a wallet")
    public ResponseEntity<ApiResponse<User>> register(@Valid @RequestBody RegisterUserRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.<User>builder()
                    .success(false)
                    .message("Email already exists")
                    .build());
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser = userRepository.save(user);
        walletService.createWallet(savedUser);

        return ResponseEntity.ok(ApiResponse.<User>builder()
                .success(true)
                .message("User registered successfully")
                .data(savedUser)
                .build());
    }
}
