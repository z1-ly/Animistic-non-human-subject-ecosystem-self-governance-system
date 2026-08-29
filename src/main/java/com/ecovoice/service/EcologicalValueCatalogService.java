package com.ecovoice.service;

import com.ecovoice.domain.EcologicalValueType;
import com.ecovoice.domain.NaturalEntity;
import com.ecovoice.dto.EcologicalValueDto;
import com.ecovoice.repository.NaturalEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EcologicalValueCatalogService {

    private final NaturalEntityRepository entityRepository;

    @Transactional(readOnly = true)
    public List<EcologicalValueDto> listAll() {
        return Arrays.stream(EcologicalValueType.values())
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public EcologicalValueDto getByKey(String valueTypeKey) {
        EcologicalValueType type = require(valueTypeKey);
        return toDto(type);
    }

    @Transactional(readOnly = true)
    public Map<String, List<EcologicalValueDto>> listByMajorCategory() {
        Map<String, List<EcologicalValueDto>> grouped = new LinkedHashMap<>();
        for (EcologicalValueType type : EcologicalValueType.values()) {
            grouped.computeIfAbsent(type.getMajorCategory(), k -> new java.util.ArrayList<>())
                    .add(toDto(type));
        }
        return grouped;
    }

    public EcologicalValueType require(String valueTypeKey) {
        return EcologicalValueType.fromKey(valueTypeKey)
                .orElseThrow(() -> new IllegalArgumentException("未知生态价值类 " + valueTypeKey));
    }

    public void assertEntityMatches(EcologicalValueType type, Long entityId) {
        NaturalEntity entity = entityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("自然体不存在"));
        if (!type.matchesEntityCategory(entity.getCategory())) {
            throw new IllegalArgumentException(
                    "自然体「" + entity.getName() + "」（" + entity.getCategory() + "）"
                            + " 不属于「" + type.getCoreValueIndicator() + "」的对应链上主体范围");
        }
    }

    public double resolveRedeemAmount(String valueTypeKey, Double quantity, Double customAmount) {
        EcologicalValueType type = require(valueTypeKey);
        if (customAmount != null && customAmount > 0) {
            return customAmount;
        }
        double qty = quantity != null && quantity > 0 ? quantity : type.getDefaultQuantity();
        return type.calcTotalEco(qty);
    }

    private EcologicalValueDto toDto(EcologicalValueType type) {
        return EcologicalValueDto.builder()
                .rowId(type.getRowId())
                .valueTypeKey(type.getValueTypeKey())
                .majorCategory(type.getMajorCategory())
                .coreValueIndicator(type.getCoreValueIndicator())
                .naturalEntityScope(type.getNaturalEntityScope())
                .issuanceLogic(type.getIssuanceLogic())
                .refluxLogic(type.getRefluxLogic())
                .unit(type.getUnit())
                .ecoPricePerUnit(type.getEcoPricePerUnit())
                .defaultQuantity(type.getDefaultQuantity())
                .defaultTotalEco(type.defaultTotalEco())
                .matchingEntities(findMatching(type))
                .build();
    }

    private List<EcologicalValueDto.MatchingEntityDto> findMatching(EcologicalValueType type) {
        return entityRepository.findAll().stream()
                .filter(e -> type.matchesEntityCategory(e.getCategory()))
                .map(e -> EcologicalValueDto.MatchingEntityDto.builder()
                        .entityId(e.getId())
                        .entityName(e.getName())
                        .category(e.getCategory())
                        .build())
                .collect(Collectors.toList());
    }
}
