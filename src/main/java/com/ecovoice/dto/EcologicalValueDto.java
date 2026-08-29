package com.ecovoice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcologicalValueDto {
    private int rowId;
    private String valueTypeKey;
    private String majorCategory;
    private String coreValueIndicator;
    private String naturalEntityScope;
    private String issuanceLogic;
    private String refluxLogic;
    private String unit;
    private double ecoPricePerUnit;
    private double defaultQuantity;
    private double defaultTotalEco;
    private List<MatchingEntityDto> matchingEntities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchingEntityDto {
        private Long entityId;
        private String entityName;
        private String category;
    }
}
