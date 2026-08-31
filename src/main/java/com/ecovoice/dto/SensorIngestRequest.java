package com.ecovoice.dto;

import com.ecovoice.domain.ProtocolType;
import com.ecovoice.domain.SensorType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SensorIngestRequest {

    @NotNull
    private Long entityId;

    private Long nodeId;

    private String nodeCode;

    @NotNull
    private SensorType sensorType;

    @NotNull
    private Double value;

    private ProtocolType protocol;
}
