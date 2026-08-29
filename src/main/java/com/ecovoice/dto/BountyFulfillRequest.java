package com.ecovoice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BountyFulfillRequest {

    @NotNull
    private Long humanNodeId;

    private String proofNote;
}
