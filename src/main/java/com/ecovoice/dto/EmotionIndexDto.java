package com.ecovoice.dto;

import com.ecovoice.domain.EmotionState;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EmotionIndexDto {

    private Long entityId;
    private String entityName;
    private double index;
    private EmotionState state;
    private String stateLabel;
    private String stateColor;
    private String summary;
    private List<String> factors;
    private LocalDateTime evaluatedAt;
}
