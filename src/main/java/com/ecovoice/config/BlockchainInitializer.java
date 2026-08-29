package com.ecovoice.config;

import com.ecovoice.domain.NaturalEntity;
import com.ecovoice.repository.NaturalEntityRepository;
import com.ecovoice.service.BlockchainLedgerService;
import com.ecovoice.service.EntityIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
public class BlockchainInitializer implements CommandLineRunner {

    private final BlockchainLedgerService ledgerService;
    private final EntityIdentityService identityService;
    private final NaturalEntityRepository entityRepository;

    @Override
    public void run(String... args) {
        ledgerService.initGenesisIfNeeded();
        entityRepository.findAll().forEach(this::ensureIdentity);
    }

    private void ensureIdentity(NaturalEntity entity) {
        double balance = resolveInitialBalance(entity);
        identityService.ensureIdentity(entity, balance);
    }

    private double resolveInitialBalance(NaturalEntity entity) {
        return 0;
    }
}
