package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 生态借贷智能合约
 * 自然主体以未来生态增量为信用背书，申请生态币借贷用于启动生态修复
 */
@Entity
@Table(name = "ecological_credit_contracts", indexes = {
        @Index(name = "idx_credit_entity", columnList = "entity_id"),
        @Index(name = "idx_credit_status", columnList = "contract_status"),
        @Index(name = "idx_credit_deadline", columnList = "repayment_deadline")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EcologicalCreditContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_address", nullable = false, unique = true, length = 66)
    private String contractAddress;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "entity_name", length = 80)
    private String entityName;

    @Column(name = "credit_amount", nullable = false)
    private Double creditAmount;

    @Column(name = "eco_token_locked", nullable = false)
    private Double ecoTokenLocked;

    @Column(name = "estimated_carbon_sink", nullable = false)
    private Double estimatedCarbonSink;

    @Column(name = "estimated_water_purification", nullable = false)
    private Double estimatedWaterPurification;

    @Column(name = "estimated_water_conservation", nullable = false)
    private Double estimatedWaterConservation;

    @Column(name = "estimated_biodiversity", nullable = false)
    private Double estimatedBiodiversity;

    @Column(name = "estimated_climate_regulation", nullable = false)
    private Double estimatedClimateRegulation;

    @Column(name = "repair_budget", nullable = false)
    private Double repairBudget;

    @Column(name = "monitoring_days", nullable = false)
    private Integer monitoringDays;

    @Column(name = "repair_days", nullable = false)
    private Integer repairDays;

    @Column(name = "total_days", nullable = false)
    private Integer totalDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_status", nullable = false, length = 30)
    private CreditContractStatus contractStatus;

    @Column(name = "application_reason", length = 500)
    private String applicationReason;

    @Column(name = "ai_assessment_report", columnDefinition = "TEXT")
    private String aiAssessmentReport;

    @Column(name = "baseline_data_snapshot", columnDefinition = "TEXT")
    private String baselineDataSnapshot;

    @Column(name = "repayment_deadline")
    private LocalDateTime repaymentDeadline;

    @Column(name = "actual_carbon_sink")
    private Double actualCarbonSink;

    @Column(name = "actual_water_purification")
    private Double actualWaterPurification;

    @Column(name = "actual_water_conservation")
    private Double actualWaterConservation;

    @Column(name = "actual_biodiversity")
    private Double actualBiodiversity;

    @Column(name = "actual_climate_regulation")
    private Double actualClimateRegulation;

    @Column(name = "total_eco_minted")
    private Double totalEcoMinted;

    @Column(name = "repayment_amount")
    private Double repaymentAmount;

    @Column(name = "repayment_status", length = 30)
    private String repaymentStatus;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (contractStatus == null) {
            contractStatus = CreditContractStatus.PENDING;
        }
        if (totalDays == null && repairDays != null && monitoringDays != null) {
            totalDays = repairDays + monitoringDays;
        }
    }
}