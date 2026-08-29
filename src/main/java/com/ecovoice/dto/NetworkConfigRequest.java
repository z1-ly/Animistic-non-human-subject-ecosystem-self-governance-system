package com.ecovoice.dto;

import com.ecovoice.domain.ProtocolType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NetworkConfigRequest {

    @NotNull
    private Long entityId;

    @NotNull
    private ProtocolType protocol;

    private String endpoint;

    private String topic;
}
