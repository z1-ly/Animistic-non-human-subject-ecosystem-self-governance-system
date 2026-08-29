package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 生态修复工程任务
 * 按工序权重自动拆分工程款，涵盖除藻净水、水生植被重构、生态增氧、生物群落修复等板块
 */
@Entity
@Table(name = "restoration_tasks", indexes = {
        @Index(name = "idx_task_contract", columnList = "contract_id"),
        @Index(name = "idx_task_status", columnList = "task_status"),
        @Index(name = "idx_task_type", columnList = "task_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestorationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 30)
    private RestorationTaskType taskType;

    @Column(name = "task_name", length = 100)
    private String taskName;

    @Column(name = "task_description", length = 500)
    private String taskDescription;

    @Column(name = "budget_amount", nullable = false)
    private Double budgetAmount;

    @Column(name = "released_amount", nullable = false)
    private Double releasedAmount;

    @Column(name = "task_weight", nullable = false)
    private Double taskWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_status", nullable = false, length = 30)
    private TaskStatus taskStatus;

    @Column(name = "contractor_name", length = 100)
    private String contractorName;

    @Column(name = "contractor_wallet", length = 66)
    private String contractorWallet;

    @Column(name = "completion_percentage", nullable = false)
    private Double completionPercentage;

    @Column(name = "ai_verification_score")
    private Double aiVerificationScore;

    @Column(name = "site_photos", columnDefinition = "TEXT")
    private String sitePhotos;

    @Column(name = "before_after_comparison", columnDefinition = "TEXT")
    private String beforeAfterComparison;

    @Column(name = "sensor_data_snapshot", columnDefinition = "TEXT")
    private String sensorDataSnapshot;

    @Column(name = "verification_report", columnDefinition = "TEXT")
    private String verificationReport;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (taskStatus == null) {
            taskStatus = TaskStatus.PENDING;
        }
        if (completionPercentage == null) {
            completionPercentage = 0.0;
        }
        if (releasedAmount == null) {
            releasedAmount = 0.0;
        }
    }
}