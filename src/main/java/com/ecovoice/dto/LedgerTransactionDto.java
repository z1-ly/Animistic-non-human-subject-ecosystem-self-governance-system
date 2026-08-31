package com.ecovoice.dto;

import com.ecovoice.domain.LedgerTransaction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LedgerTransactionDto {

    private Long id;
    private String txHash;
    private String type;
    private String typeLabel;
    private Long entityId;
    private Long appealId;
    private String fromAddress;
    private String toAddress;
    private double amount;
    private String payload;
    private LocalDateTime createdAt;

    public static LedgerTransactionDto from(LedgerTransaction tx) {
        return LedgerTransactionDto.builder()
                .id(tx.getId())
                .txHash(tx.getTxHash())
                .type(tx.getType().name())
                .typeLabel(tx.getType().getLabel())
                .entityId(tx.getEntityId())
                .appealId(tx.getAppealId())
                .fromAddress(tx.getFromAddress())
                .toAddress(tx.getToAddress())
                .amount(tx.getAmount())
                .payload(tx.getPayload())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
