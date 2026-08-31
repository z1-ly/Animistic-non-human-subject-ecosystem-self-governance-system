package com.ecovoice.dto;

import com.ecovoice.domain.EntityIdentity;
import com.ecovoice.domain.NaturalEntity;
import com.ecovoice.repository.NaturalEntityRepository;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EntityIdentityDto {

    private Long entityId;
    private String entityName;
    private String did;
    private String walletAddress;
    private String legalPersonId;
    private String aiAgentId;
    private double walletBalance;
    private double lockedBalance;
    private double availableBalance;
    private double totalEarned;
    private double totalSpent;
    private double borrowedBalance;
    private LocalDateTime createdAt;

    public static EntityIdentityDto from(EntityIdentity identity, String entityName) {
        return EntityIdentityDto.builder()
                .entityId(identity.getEntityId())
                .entityName(entityName)
                .did(identity.getDid())
                .walletAddress(identity.getWalletAddress())
                .legalPersonId(identity.getLegalPersonId())
                .aiAgentId(identity.getAiAgentId())
                .walletBalance(identity.getWalletBalance())
                .lockedBalance(identity.getLockedBalance())
                .availableBalance(identity.getWalletBalance() - identity.getLockedBalance())
                .totalEarned(identity.getTotalEarned())
                .totalSpent(identity.getTotalSpent())
                .borrowedBalance(identity.getBorrowedBalance())
                .createdAt(identity.getCreatedAt())
                .build();
    }
}
