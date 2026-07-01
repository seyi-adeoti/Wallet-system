package com.wallet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementReport {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private LocalDate reportDate;

    @Column(nullable = false)
    private Integer totalTransactions;

    @Column(nullable = false)
    private Integer successfulTransactions;

    @Column(nullable = false)
    private Integer failedTransactions;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal settledAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal pendingAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal charges;

    @Column(precision = 19, scale = 2)
    private BigDecimal bankCharges;

    @Column(precision = 19, scale = 2)
    private BigDecimal systemCharges;

    @Column(length = 20)
    private String status;

    @Column
    private LocalDateTime generatedAt;

    @Column
    private LocalDateTime settledAt;

    @Column(length = 500)
    private String notes;

    @PrePersist
    protected void onCreate() {
        generatedAt = LocalDateTime.now();
        if (status == null) {
            status = "DRAFT";
        }
    }
}