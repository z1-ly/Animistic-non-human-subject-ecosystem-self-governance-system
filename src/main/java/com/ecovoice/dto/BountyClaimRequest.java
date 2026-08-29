package com.ecovoice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BountyClaimRequest {

    @NotNull
    private Long humanNodeId;
}
