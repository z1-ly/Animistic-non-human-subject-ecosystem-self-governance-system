package com.ecovoice.repository;

import com.ecovoice.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    List<LedgerTransaction> findByEntityIdOrderByCreatedAtDesc(Long entityId);

    List<LedgerTransaction> findTop50ByOrderByCreatedAtDesc();
}
