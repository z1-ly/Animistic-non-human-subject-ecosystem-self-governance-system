package com.ecovoice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceExchangeRequest {
    
    private String resourceCode;
    
    private String valueTypeKey;
    
    private Integer quantity;
    
    private Long targetEntityId;
}
