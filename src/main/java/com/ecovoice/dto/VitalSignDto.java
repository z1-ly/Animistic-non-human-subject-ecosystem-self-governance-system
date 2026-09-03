package com.ecovoice.dto;

import com.ecovoice.domain.ProtocolType;
import com.ecovoice.domain.SensorType;
import com.ecovoice.domain.VitalSignRecord;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class VitalSignDto {

    private Long id;
    private Long entityId;
    private String entityName;
    private Long nodeId;
    private String nodeCode;
    private SensorType sensorType;
    private String sensorLabel;
    private String unit;
    private double value;
    private String status;
    private ProtocolType protocol;
    private String protocolLabel;
    private LocalDateTime recordedAt;

    public static VitalSignDto from(VitalSignRecord record, String entityName, String nodeCode) {
        SensorType type = record.getSensorType();
        double v = record.getValue();
        String status = "NORMAL";
        if (v < type.getNormalMin() || v > type.getNormalMax()) {
            status = "ALERT";
        }
        return VitalSignDto.builder()
                .id(record.getId())
                .entityId(record.getEntityId())
                .entityName(entityName)
                .nodeId(record.getNodeId())
                .nodeCode(nodeCode)
                .sensorType(type)
                .sensorLabel(type.getLabel())
                .unit(type.getUnit())
                .value(v)
                .status(status)
                .protocol(record.getProtocol())
                .protocolLabel(record.getProtocol().getLabel())
                .recordedAt(record.getRecordedAt())
                .build();
    }
}
