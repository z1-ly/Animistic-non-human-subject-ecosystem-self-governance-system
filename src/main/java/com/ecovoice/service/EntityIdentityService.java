package com.ecovoice.service;

import com.ecovoice.domain.*;
import com.ecovoice.dto.EntityIdentityDto;
import com.ecovoice.repository.EntityIdentityRepository;
import com.ecovoice.repository.NaturalEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EntityIdentityService {

    private final EntityIdentityRepository identityRepository;
    private final NaturalEntityRepository entityRepository;
    private final BlockchainLedgerService ledgerService;

    @Transactional
    public EntityIdentity ensureIdentity(NaturalEntity entity, double initialBalance) {
        return identityRepository.findById(entity.getId()).orElseGet(() -> {
            String slug = entity.getName().replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "").toLowerCase();
            if (slug.length() > 20) slug = slug.substring(0, 20);
            EntityIdentity identity = identityRepository.save(EntityIdentity.builder()
                    .entityId(entity.getId())
                    .did("did:ecovoice:" + entity.getId() + ":" + slug)
                    .walletAddress(ledgerService.generateWalletAddress())
                    .legalPersonId("NHE-LP-" + String.format("%06d", entity.getId()))
                    .aiAgentId("AGENT-AI-" + String.format("%06d", entity.getId()))
                    .walletBalance(initialBalance)
                    .lockedBalance(0)
                    .totalEarned(initialBalance)
                    .totalSpent(0)
                    .build());
            ledgerService.appendTransaction(
                    LedgerTransactionType.GENESIS,
                    entity.getId(), null,
                    "0x0000000000000000000000000000000000000000",
                    identity.getWalletAddress(),
                    initialBalance,
                    "自然体「" + entity.getName() + "」生态币创世储备 " + initialBalance + " " + BlockchainLedgerService.TOKEN_SYMBOL);
            return identity;
        });
    }

    public EntityIdentityDto getIdentity(Long entityId) {
        NaturalEntity entity = entityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("自然体不存在"));
        EntityIdentity identity = identityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("数字身份未注册"));
        return EntityIdentityDto.from(identity, entity.getName());
    }

    public List<EntityIdentityDto> listIdentities() {
        return identityRepository.findAll().stream()
                .map(id -> {
                    String name = entityRepository.findById(id.getEntityId())
                            .map(NaturalEntity::getName)
                            .orElse("未知主体");
                    return EntityIdentityDto.from(id, name);
                })
                .toList();
    }

    @Transactional
    public void creditDataRoyalty(Long entityId, double amount, String reason) {
        EntityIdentity identity = identityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("数字身份未注册"));
        identity.setWalletBalance(identity.getWalletBalance() + amount);
        identity.setTotalEarned(identity.getTotalEarned() + amount);
        identityRepository.save(identity);
        ledgerService.appendTransaction(
                LedgerTransactionType.DATA_ROYALTY,
                entityId, null,
                "0x0000000000000000000000000000000000000000",
                identity.getWalletAddress(),
                amount,
                reason + " · " + amount + " " + BlockchainLedgerService.TOKEN_SYMBOL);
    }
}
