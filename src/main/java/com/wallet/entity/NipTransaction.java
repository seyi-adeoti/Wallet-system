package com.wallet.entity;

import com.wallet.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "nip_transactions")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NipTransaction {

    @Id @GeneratedValue
    private UUID id;

    private UUID senderWalletId;
    private String senderAccount;
    private String receiverAccount;
    private String receiverBankCode;
    private String receiverName;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(precision = 19, scale = 4)
    private BigDecimal fee;

    @Column(unique = true)
    private String reference;

    @Column(unique = true)
    private String sessionId;  // assigned by NIBSS

    private String narration;

    @Enumerated(EnumType.STRING)
    private NipStatus status;

    private String responseCode;
    private String responseMessage;
    private int retryCount;

    private LocalDateTime nibssSentAt;
    private LocalDateTime nibssRespondedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}