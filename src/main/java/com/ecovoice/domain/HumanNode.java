package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "human_nodes", indexes = {
        @Index(name = "idx_human_did", columnList = "did", unique = true),
        @Index(name = "idx_human_wallet", columnList = "wallet_address", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HumanNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, unique = true, length = 120)
    private String did;

    @Column(name = "wallet_address", nullable = false, unique = true, length = 66)
    private String walletAddress;

    @Column(name = "peer_node_id", nullable = false, unique = true, length = 32)
    private String peerNodeId;

    @Column(name = "wallet_balance", nullable = false)
    private double walletBalance;

    @Column(name = "total_earned", nullable = false)
    private double totalEarned;

    @Column(name = "total_spent", nullable = false)
    private double totalSpent;

    @Column(name = "tasks_completed", nullable = false)
    private int tasksCompleted;

    @Column(name = "tasks_claimed", nullable = false)
    private int tasksClaimed;

    private boolean online;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        online = true;
    }
}
