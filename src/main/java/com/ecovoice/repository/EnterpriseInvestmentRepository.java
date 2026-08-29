package com.ecovoice.repository;

import com.ecovoice.domain.EnterpriseInvestment;
import com.ecovoice.domain.InvestmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnterpriseInvestmentRepository extends JpaRepository<EnterpriseInvestment, Long> {

    List<EnterpriseInvestment> findByContractId(Long contractId);

    List<EnterpriseInvestment> findByHumanNodeId(Long humanNodeId);

    List<EnterpriseInvestment> findByInvestmentStatus(InvestmentStatus status);

    @Query("SELECT SUM(i.investmentAmount) FROM EnterpriseInvestment i WHERE i.contractId = :contractId AND i.investmentStatus = 'LOCKED'")
    Double getTotalFundingByContractId(@Param("contractId") Long contractId);

    @Query("SELECT i FROM EnterpriseInvestment i WHERE i.contractId = :contractId AND i.investmentStatus = 'LOCKED'")
    List<EnterpriseInvestment> findLockedInvestmentsByContractId(@Param("contractId") Long contractId);
}