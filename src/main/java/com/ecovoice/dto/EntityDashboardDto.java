package com.ecovoice.dto;

import com.ecovoice.domain.ProtocolType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class EntityDashboardDto {

    private Long id;
    private String name;
    private String category;
    private String location;
    private String description;
    private String status;
    private String avatarColor;
    private ProtocolType protocol;
    private String protocolLabel;
    private int nodeCount;
    private long totalReadings;
    private List<SensorSnapshotDto> sensors;
    private Map<String, List<ChartPointDto>> chartSeries;
}
