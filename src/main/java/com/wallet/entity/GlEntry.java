package com.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gl_entries")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GlEntry {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gl_account_id")
    private GlAccount glAccount;

    @Enumerated(EnumType.STRING)
    private TransactionType entryType; // DEBIT or CREDIT

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    private String reference;
    private String narration;
    private LocalDate transactionDate;

    @CreationTimestamp
    private LocalDateTime postedAt;
}