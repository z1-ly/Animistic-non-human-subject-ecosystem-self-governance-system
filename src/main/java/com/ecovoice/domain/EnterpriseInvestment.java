package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 企业投资记录
 * 企业出资抵押认购生态修复项目，锁定生态币额度，风险自担
 */
@Entity
@Table(name = "enterprise_investments", indexes = {
        @Index(name = "idx_investment_contract", columnList = "contract_id"),
        @Index(name = "idx_investment_human", columnList = "human_node_id"),
        @Index(name = "idx_investment_status", columnList = "investment_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnterpriseInvestment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "human_node_id", nullable = false)
    private Long humanNodeId;

    @Column(name = "enterprise_name", length = 100)
    private String enterpriseName;

    @Column(name = "investment_amount", nullable = false)
    private Double investmentAmount;

    @Column(name = "eco_token_locked", nullable = false)
    private Double ecoTokenLocked;

    @Column(name = "investment_ratio", nullable = false)
    private Double investmentRatio;

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_status", nullable = false, length = 30)
    private InvestmentStatus investmentStatus;

    @Column(name = "expected_eco_return")
    private Double expectedEcoReturn;

    @Column(name = "actual_eco_return")
    private Double actualEcoReturn;

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

    @Column(name = "settlement_report", columnDefinition = "TEXT")
    private String settlementReport;

    @Column(name = "invested_at")
    private LocalDateTime investedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (investmentStatus == null) {
            investmentStatus = InvestmentStatus.PENDING;
        }
    }
}