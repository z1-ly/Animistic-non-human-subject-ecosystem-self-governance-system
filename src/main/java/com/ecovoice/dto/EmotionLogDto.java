package com.ecovoice.dto;

import com.ecovoice.domain.EmotionLog;
import com.ecovoice.domain.EmotionState;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EmotionLogDto {

    private Long id;
    private Long entityId;
    private EmotionState emotionState;
    private String stateLabel;
    private String stateColor;
    private double emotionIndex;
    private String narrative;
    private LocalDateTime createdAt;

    public static EmotionLogDto from(EmotionLog log) {
        return EmotionLogDto.builder()
                .id(log.getId())
                .entityId(log.getEntityId())
                .emotionState(log.getEmotionState())
                .stateLabel(log.getEmotionState().getLabel())
                .stateColor(log.getEmotionState().getColor())
                .emotionIndex(log.getEmotionIndex())
                .narrative(log.getNarrative())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
