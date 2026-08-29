package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "nature_appeals", indexes = {
        @Index(name = "idx_appeal_entity_active", columnList = "entity_id, appeal_type, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NatureAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "appeal_type", nullable = false, length = 30)
    private AppealType appealType;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(length = 200)
    private String triggerRule;

    @Column(length = 20)
    private String severity;

    @Column(name = "action_detail", length = 500)
    private String actionDetail;

    @Column(name = "action_parameters", length = 1000)
    private String actionParameters;

    @Column(name = "measurable_target", length = 200)
    private String measurableTarget;

    @Column(name = "acceptance_criteria", length = 1000)
    private String acceptanceCriteria;

    @Column(name = "baseline_data", length = 500)
    private String baselineData;

    @Column(name = "reward_rules", length = 500)
    private String rewardRules;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "bounty_amount")
    private Double bountyAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "bounty_status", length = 20)
    private BountyStatus bountyStatus;

    @Column(name = "contract_address", length = 66)
    private String contractAddress;

    @Column(name = "contract_tx_hash", length = 64)
    private String contractTxHash;

    @Column(name = "fulfiller_name", length = 80)
    private String fulfillerName;

    @Column(name = "claimant_node_id")
    private Long claimantNodeId;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;

    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (severity == null) {
            severity = "MEDIUM";
        }
        active = true;
    }
}
