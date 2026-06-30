package com.wallet.service;


import com.wallet.entity.Wallet;
import com.wallet.repository.*
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;


@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {

    private final KycUpgradeRepository kycUpgradeRepository;
    private final UserRepository userRepository;

    // User submits upgrade request
    @Transactional
    public KycUpgradeResponse requestUpgrade(UUID userId, KycUpgradeRequest request) {

        User user = userRepository.findById(userId).orElseThrow();

        // Can't skip tiers
        KycTier currentTier = user.getKycTier();
        KycTier requestedTier = KycTier.valueOf(request.getRequestedTier());

        if (!isValidUpgrade(currentTier, requestedTier)) {
            throw new WalletException(
                String.format("Cannot upgrade from %s to %s directly", currentTier, requestedTier)
            );
        }

        // Check no pending request exists
        if (kycUpgradeRepository.existsByUserIdAndStatus(userId, "PENDING")) {
            throw new WalletException("You already have a pending KYC upgrade request");
        }

        // Tier 2 requires BVN, Tier 3 requires NIN + address
        validateDocumentsForTier(requestedTier, request);

        KycUpgrade upgrade = KycUpgrade.builder()
                .user(user)
                .requestedTier(requestedTier.name())
                .bvn(request.getBvn())
                .nin(request.getNin())
                .address(request.getAddress())
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber())
                .status("PENDING")
                .build();

        kycUpgradeRepository.save(upgrade);

        // In production: trigger BVN verification via NIBSS
        // Here: auto-approve for simulation
        autoApproveForSimulation(upgrade, user);

        log.info("KYC upgrade requested → User:{} Tier:{}", userId, requestedTier);

        return KycUpgradeResponse.builder()
                .status("PENDING")
                .requestedTier(requestedTier.name())
                .message("KYC upgrade submitted. Verification in progress.")
                .build();
    }

    // In production this is a manual review or NIBSS BVN call
    private void autoApproveForSimulation(KycUpgrade upgrade, User user) {
        upgrade.setStatus("APPROVED");
        upgrade.setReviewedAt(LocalDateTime.now());
        kycUpgradeRepository.save(upgrade);

        // Upgrade user tier
        user.setKycTier(KycTier.valueOf(upgrade.getRequestedTier()));
        user.setKycStatus("VERIFIED");

        // Store BVN/NIN on user record
        if (upgrade.getBvn() != null) user.setBvn(upgrade.getBvn());
        if (upgrade.getNin() != null) user.setNin(upgrade.getNin());

        userRepository.save(user);

        log.info("KYC auto-approved → User:{} NewTier:{}", user.getId(), upgrade.getRequestedTier());
    }

    private boolean isValidUpgrade(KycTier current, KycTier requested) {
        return switch (current) {
            case TIER_1 -> requested == KycTier.TIER_2;
            case TIER_2 -> requested == KycTier.TIER_3;
            case TIER_3 -> false; // already at max
        };
    }

    private void validateDocumentsForTier(KycTier tier, KycUpgradeRequest request) {
        if (tier == KycTier.TIER_2) {
            if (request.getBvn() == null || request.getBvn().length() != 11) {
                throw new WalletException("Valid 11-digit BVN is required for Tier 2");
            }
        }
        if (tier == KycTier.TIER_3) {
            if (request.getNin() == null || request.getNin().length() != 11) {
                throw new WalletException("Valid NIN is required for Tier 3");
            }
            if (request.getAddress() == null || request.getAddress().isBlank()) {
                throw new WalletException("Address is required for Tier 3");
            }
        }
    }

    public KycStatusResponse getKycStatus(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        KycTierLimit limit = getTierLimits(user.getKycTier());

        return KycStatusResponse.builder()
                .currentTier(user.getKycTier().name())
                .kycStatus(user.getKycStatus())
                .singleTransferLimit(limit.getSingleTransferLimit())
                .dailyDebitLimit(limit.getDailyDebitLimit())
                .maxWalletBalance(limit.getMaxWalletBalance())
                .canUpgrade(user.getKycTier() != KycTier.TIER_3)
                .nextTier(getNextTier(user.getKycTier()))
                .build();
    }
}