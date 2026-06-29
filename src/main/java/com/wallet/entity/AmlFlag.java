package com.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "aml_flags")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AmlFlag {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private UUID walletId;
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    private AmlFlagType flagType = AmlFlagType.NOT_SUSPICIOUS;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    private String description;
    private String status = "OPEN";

    private String reviewedBy;
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}