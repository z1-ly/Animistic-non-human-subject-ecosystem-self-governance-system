package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 生态币清算记录
 * 按企业投资比例精准分配生态币，缺口由企业自行承担
 */
@Entity
@Table(name = "eco_token_settlement", indexes = {
        @Index(name = "idx_settlement_contract", columnList = "contract_id"),
        @Index(name = "idx_settlement_investment", columnList = "investment_id"),
        @Index(name = "idx_settlement_status", columnList = "settlement_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EcoTokenSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "investment_id", nullable = false)
    private Long investmentId;

    @Column(name = "human_node_id", nullable = false)
    private Long humanNodeId;

    @Column(name = "enterprise_name", length = 100)
    private String enterpriseName;

    @Column(name = "investment_ratio", nullable = false)
    private Double investmentRatio;

    @Column(name = "investment_amount", nullable = false)
    private Double investmentAmount;

    @Column(name = "eco_token_locked", nullable = false)
    private Double ecoTokenLocked;

    @Column(name = "total_eco_allocated", nullable = false)
    private Double totalEcoAllocated;

    @Column(name = "carbon_sink_allocated")
    private Double carbonSinkAllocated;

    @Column(name = "water_purification_allocated")
    private Double waterPurificationAllocated;

    @Column(name = "water_conservation_allocated")
    private Double waterConservationAllocated;

    @Column(name = "biodiversity_allocated")
    private Double biodiversityAllocated;

    @Column(name = "climate_regulation_allocated")
    private Double climateRegulationAllocated;

    @Column(name = "other_tokens_allocated")
    private Double otherTokensAllocated;

    @Column(name = "expected_return")
    private Double expectedReturn;

    @Column(name = "actual_return")
    private Double actualReturn;

    @Column(name = "return_rate")
    private Double returnRate;

    @Column(name = "shortfall_amount")
    private Double shortfallAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 30)
    private SettlementStatus settlementStatus;

    @Column(name = "settlement_report", columnDefinition = "TEXT")
    private String settlementReport;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (settlementStatus == null) {
            settlementStatus = SettlementStatus.PENDING;
        }
        if (actualReturn != null && investmentAmount != null && investmentAmount > 0) {
            returnRate = Math.round((actualReturn / investmentAmount) * 10000.0) / 100.0;
        }
    }
}