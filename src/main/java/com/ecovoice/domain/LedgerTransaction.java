package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_transactions", indexes = {
        @Index(name = "idx_tx_hash", columnList = "tx_hash", unique = true),
        @Index(name = "idx_tx_entity", columnList = "entity_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_id", nullable = false)
    private Long blockId;

    @Column(name = "tx_hash", nullable = false, unique = true, length = 64)
    private String txHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LedgerTransactionType type;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "appeal_id")
    private Long appealId;

    @Column(name = "from_address", length = 66)
    private String fromAddress;

    @Column(name = "to_address", length = 66)
    private String toAddress;

    @Column(nullable = false)
    private double amount;

    @Column(length = 500)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
