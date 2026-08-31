package com.ecovoice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HumanNodeRegisterRequest {

    @NotBlank
    private String displayName;
}
