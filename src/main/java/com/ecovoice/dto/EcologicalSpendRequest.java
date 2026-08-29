package com.ecovoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class EcologicalSpendRequest {
    @NotBlank
    private String spenderType; // HUMAN_NODE or ENTITY
    private Long humanNodeId;
    private Long entityId;

    @NotBlank
    private String spendCategory; // EMISSION, WATER, LAND, RESOURCE, DESTRUCTION, DAO_FEE
    private String reference;
    private String note;

    @PositiveOrZero
    private double baseAmount;
    private double severityMultiplier; // 默认 1.0；重度破坏 >=2
}
