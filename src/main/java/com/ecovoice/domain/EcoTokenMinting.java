package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 多维生态币铸造记录
 * 按碳汇、水质净化、水源涵养、生物多样性、气候调节等品类铸造细分生态币
 */
@Entity
@Table(name = "eco_token_minting", indexes = {
        @Index(name = "idx_minting_contract", columnList = "contract_id"),
        @Index(name = "idx_minting_entity", columnList = "entity_id"),
        @Index(name = "idx_minting_type", columnList = "token_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EcoTokenMinting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "entity_name", length = 80)
    private String entityName;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 30)
    private EcoTokenType tokenType;

    @Column(name = "actual_value", nullable = false)
    private Double actualValue;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "eco_price_per_unit", nullable = false)
    private Double ecoPricePerUnit;

    @Column(name = "eco_amount_minted", nullable = false)
    private Double ecoAmountMinted;

    @Column(name = "minting_tx_hash", length = 64)
    private String mintingTxHash;

    @Column(name = "ai_calculation_report", columnDefinition = "TEXT")
    private String aiCalculationReport;

    @Column(name = "monitoring_period_start")
    private LocalDateTime monitoringPeriodStart;

    @Column(name = "monitoring_period_end")
    private LocalDateTime monitoringPeriodEnd;

    @Column(name = "minted_at")
    private LocalDateTime mintedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (ecoAmountMinted == null && actualValue != null && ecoPricePerUnit != null) {
            ecoAmountMinted = Math.round(actualValue * ecoPricePerUnit * 100.0) / 100.0;
        }
    }
}