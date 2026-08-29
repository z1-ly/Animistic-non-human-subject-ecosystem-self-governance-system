package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_blocks", indexes = {
        @Index(name = "idx_block_index", columnList = "block_index", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_index", nullable = false, unique = true)
    private long blockIndex;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "mined_at", nullable = false)
    private LocalDateTime minedAt;

    @Column(length = 40)
    private String miner;

    @PrePersist
    void onCreate() {
        if (minedAt == null) {
            minedAt = LocalDateTime.now();
        }
        if (miner == null) {
            miner = "EcoVoice-Node";
        }
    }
}
