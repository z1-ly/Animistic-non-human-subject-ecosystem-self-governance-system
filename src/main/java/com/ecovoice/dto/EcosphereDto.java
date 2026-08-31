package com.ecovoice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcosphereDto {
    private String tokenName;          // 生态币
    private String tokenSymbol;        // ECO
    private double totalSupply;        // 恒定总量
    private double circulatingSupply;  // 当前在各自然体与人类钱包流通的总量
    private double entityReserve;      // 自然体总储备
    private double humanReserve;       // 人类节点总储备
    private double smartContractAvailable;  // 智能合约可用余额
    private double smartContractLent;  // 智能合约已借出余额
    private long entityCount;
    private long humanNodeCount;
    private Map<String, Double> ecosystemReserve;
    private Map<String, Object> latestSettle;
}
