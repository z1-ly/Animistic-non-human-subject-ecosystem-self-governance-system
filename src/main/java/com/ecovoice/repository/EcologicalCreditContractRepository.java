package com.ecovoice.repository;

import com.ecovoice.domain.CreditContractStatus;
import com.ecovoice.domain.EcologicalCreditContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EcologicalCreditContractRepository extends JpaRepository<EcologicalCreditContract, Long> {

    List<EcologicalCreditContract> findByEntityId(Long entityId);

    List<EcologicalCreditContract> findByContractStatus(CreditContractStatus status);

    List<EcologicalCreditContract> findByContractStatusIn(List<CreditContractStatus> statuses);

    Optional<EcologicalCreditContract> findByContractAddress(String contractAddress);

    @Query("SELECT c FROM EcologicalCreditContract c WHERE c.contractStatus = 'IN_MONITORING' AND c.repaymentDeadline <= :deadline")
    List<EcologicalCreditContract> findContractsNeedingVerification(@Param("deadline") LocalDateTime deadline);

    @Query("SELECT c FROM EcologicalCreditContract c WHERE c.contractStatus = 'IN_REPAIR' AND c.createdAt <= :startDate")
    List<EcologicalCreditContract> findContractsInRepairExceedingPeriod(@Param("startDate") LocalDateTime startDate);
}