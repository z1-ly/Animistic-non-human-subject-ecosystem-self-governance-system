package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "entity_identities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityIdentity {

    @Id
    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false, unique = true, length = 120)
    private String did;

    @Column(name = "wallet_address", nullable = false, unique = true, length = 66)
    private String walletAddress;

    @Column(name = "legal_person_id", length = 64)
    private String legalPersonId;

    @Column(name = "ai_agent_id", length = 64)
    private String aiAgentId;

    @Column(name = "wallet_balance", nullable = false)
    private double walletBalance;

    @Column(name = "locked_balance", nullable = false)
    private double lockedBalance;

    @Column(name = "total_earned", nullable = false)
    private double totalEarned;

    @Column(name = "total_spent", nullable = false)
    private double totalSpent;

    @Column(name = "borrowed_balance", nullable = false)
    private double borrowedBalance = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (borrowedBalance == 0) {
            borrowedBalance = 0;
        }
    }
}
