package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 智能合约 - 生态币资金池
 * 管理恒定总量的生态币，作为所有借贷和分配的资金来源
 */
@Entity
@Table(name = "smart_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_address", nullable = false, unique = true, length = 66)
    private String contractAddress;

    @Column(name = "contract_name", nullable = false, length = 100)
    private String contractName;

    @Column(name = "total_supply", nullable = false)
    private Double totalSupply;

    @Column(name = "available_balance", nullable = false)
    private Double availableBalance;

    @Column(name = "lent_balance", nullable = false)
    private Double lentBalance;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (contractAddress == null) {
            contractAddress = "0x" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        }
    }

    /**
     * 借出生态币
     */
    public void lend(double amount) {
        if (availableBalance < amount) {
            throw new IllegalStateException("智能合约可用余额不足");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("借币金额必须大于0");
        }
        this.availableBalance -= amount;
        this.lentBalance += amount;
    }

    /**
     * 回收生态币
     */
    public void recover(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("还币金额必须大于0");
        }
        if (lentBalance < amount) {
            throw new IllegalStateException("回收金额超过已借出余额");
        }
        this.availableBalance += amount;
        this.lentBalance -= amount;
    }

    /**
     * 分配生态币（从可用余额中分配，不增加总量）
     */
    public void allocate(double amount) {
        if (availableBalance < amount) {
            throw new IllegalStateException("智能合约可用余额不足");
        }
        this.availableBalance -= amount;
    }

    /**
     * 获取资金池使用率
     */
    public double getUtilizationRate() {
        return totalSupply > 0 ? (lentBalance / totalSupply) * 100 : 0;
    }
}