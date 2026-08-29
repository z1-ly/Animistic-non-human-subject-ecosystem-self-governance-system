package com.ecovoice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EntityInsightDto {

    private EmotionIndexDto emotion;
    private List<NatureAppealDto> appeals;
    private List<EmotionLogDto> logs;
}
