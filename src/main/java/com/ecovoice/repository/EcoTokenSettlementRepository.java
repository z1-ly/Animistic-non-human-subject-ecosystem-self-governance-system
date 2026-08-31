package com.ecovoice.repository;

import com.ecovoice.domain.EcoTokenSettlement;
import com.ecovoice.domain.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EcoTokenSettlementRepository extends JpaRepository<EcoTokenSettlement, Long> {

    List<EcoTokenSettlement> findByContractId(Long contractId);

    List<EcoTokenSettlement> findByInvestmentId(Long investmentId);

    List<EcoTokenSettlement> findByHumanNodeId(Long humanNodeId);

    List<EcoTokenSettlement> findBySettlementStatus(SettlementStatus status);

    @Query("SELECT SUM(s.totalEcoAllocated) FROM EcoTokenSettlement s WHERE s.contractId = :contractId")
    Double getTotalAllocatedByContractId(@Param("contractId") Long contractId);
}