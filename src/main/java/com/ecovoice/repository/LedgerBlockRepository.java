package com.ecovoice.repository;

import com.ecovoice.domain.LedgerBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerBlockRepository extends JpaRepository<LedgerBlock, Long> {

    Optional<LedgerBlock> findTopByOrderByBlockIndexDesc();

    Optional<LedgerBlock> findByBlockIndex(long blockIndex);
}
