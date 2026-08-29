package com.ecovoice.dto;

import com.ecovoice.domain.SensorType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SensorSnapshotDto {

    private Long nodeId;
    private String nodeCode;
    private String name;
    private SensorType sensorType;
    private String label;
    private String unit;
    private Double value;
    private boolean online;
    private String status;
    private String position;
}
