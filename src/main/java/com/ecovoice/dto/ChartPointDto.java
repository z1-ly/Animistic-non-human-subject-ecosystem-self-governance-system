package com.ecovoice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChartPointDto {

    private String time;
    private double value;
}
