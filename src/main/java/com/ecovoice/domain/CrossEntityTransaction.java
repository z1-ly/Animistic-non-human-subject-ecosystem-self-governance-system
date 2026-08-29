package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 跨主体生态服务交易
 * 自然主体之间进行生态服务结算，形成全域生态经济内循环
 */
@Entity
@Table(name = "cross_entity_transactions", indexes = {
        @Index(name = "idx_cross_from", columnList = "from_entity_id"),
        @Index(name = "idx_cross_to", columnList = "to_entity_id"),
        @Index(name = "idx_cross_type", columnList = "service_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrossEntityTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_entity_id", nullable = false)
    private Long fromEntityId;

    @Column(name = "from_entity_name", length = 80)
    private String fromEntityName;

    @Column(name = "to_entity_id", nullable = false)
    private Long toEntityId;

    @Column(name = "to_entity_name", length = 80)
    private String toEntityName;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 30)
    private EcoServiceType serviceType;

    @Column(name = "service_description", length = 500)
    private String serviceDescription;

    @Column(name = "service_quantity", nullable = false)
    private Double serviceQuantity;

    @Column(name = "service_unit", length = 20)
    private String serviceUnit;

    @Column(name = "eco_amount", nullable = false)
    private Double ecoAmount;

    @Column(name = "transaction_hash", length = 64)
    private String transactionHash;

    @Column(name = "contract_period_start")
    private LocalDateTime contractPeriodStart;

    @Column(name = "contract_period_end")
    private LocalDateTime contractPeriodEnd;

    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring;

    @Column(name = "recurrence_interval_days")
    private Integer recurrenceIntervalDays;

    @Column(name = "next_payment_date")
    private LocalDateTime nextPaymentDate;

    @Column(name = "transaction_status", length = 30)
    private String transactionStatus;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "transacted_at")
    private LocalDateTime transactedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isRecurring == null) {
            isRecurring = false;
        }
        if (transactionStatus == null) {
            transactionStatus = "COMPLETED";
        }
    }
}