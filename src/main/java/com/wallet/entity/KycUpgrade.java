package com.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;


@Entity
@Table(name = "kyc_upgrades")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class KycUpgrade {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String requestedTier;
    private String bvn;
    private String nin;
    private String address;
    private String documentType;
    private String documentNumber;

    private String status = "PENDING"; // PENDING, APPROVED, REJECTED
    private String rejectionReason;

    private LocalDateTime reviewedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}