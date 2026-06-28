package com.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "gl_accounts")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GlAccount {

    @Id @GeneratedValue
    private UUID id;

    @Column(unique = true)
    private String code;

    private String name;

    @Enumerated(EnumType.STRING)
    private GlAccountType type;  // ASSET, LIABILITY, INCOME, EXPENSE, EQUITY

    private String normalBalance; // DEBIT or CREDIT
    private String description;
    private boolean isActive;
}