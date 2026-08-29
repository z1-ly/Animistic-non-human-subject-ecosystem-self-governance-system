package com.ecovoice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class EcoCoinTransferRequest {
    @NotNull
    private Long fromEntityId;  // 转出自然体 ID
    @NotNull
    private Long toEntityId;    // 转入自然体 ID

    @Positive
    private double amount;

    private String reason;      // 例如 "碳汇结算" / "调洪服务" / "空气净化"
}
