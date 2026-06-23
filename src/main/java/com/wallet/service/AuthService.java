


package com.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.wallet.repository.UserRepository;
import com.wallet.service.WalletService;
import com.wallet.service.RateLimiterService;
import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.entity.KycTier;
import com.wallet.entity.WalletTransaction;
import com.wallet.entity.WalletTransactionType;
import com.wallet.entity.WalletTransactionStatus;
import com.wallet.entity.WalletTransactionMetadata;
import com.wallet.entity.WalletTransactionReference;
import com.wallet.entity.WalletTransactionDescription;
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RateLimiterService rateLimiterService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        // Check duplicates
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new WalletException("Email already registered");
            log.error("Email already registered: {}", request.getEmail());
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new WalletException("Phone number already registered");
            log.error("Phone number already registered: {}", request.getPhone());
        }

        // Create user
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .kycTier(KycTier.TIER_1)
                .kycStatus("PENDING")
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("User registered: {}", user.getEmail());
        // Auto-create wallet
        Wallet wallet = walletService.createWallet(user);
        log.info("Wallet created: {}", wallet.getAccountNumber());
        log.info("New user registered: {} | Account: {}", user.getEmail(), wallet.getAccountNumber());

        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .accountNumber(wallet.getAccountNumber())
                .kycTier(user.getKycTier().name())
                .message("Registration successful. Complete KYC to unlock higher limits.")
                .build();
    }

    public LoginResponse login(LoginRequest request, String clientIp) {

        // Rate limit login attempts per IP
        if (!rateLimiterService.isLoginAllowed(clientIp)) {
            throw new WalletException("Too many login attempts. Try again in 1 minute.");
        }
        log.info("Login attempt: {}", request.getEmail());
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new WalletException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new WalletException("Invalid credentials");
        }
        log.info("User found: {}", user.getEmail());        
        log.info("User active: {}", user.isActive());
        if (!user.isActive()) {
            throw new WalletException("Account is deactivated");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .kycTier(user.getKycTier().name())
                .expiresIn("24 hours")
                .build();
    }
}