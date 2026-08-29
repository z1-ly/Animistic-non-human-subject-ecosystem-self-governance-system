package com.ecovoice.repository;

import com.ecovoice.domain.RestorationTask;
import com.ecovoice.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestorationTaskRepository extends JpaRepository<RestorationTask, Long> {

    List<RestorationTask> findByContractId(Long contractId);

    List<RestorationTask> findByTaskStatus(TaskStatus status);

    List<RestorationTask> findByContractIdAndTaskStatus(Long contractId, TaskStatus status);

    @Query("SELECT SUM(r.budgetAmount) FROM RestorationTask r WHERE r.contractId = :contractId")
    Double getTotalBudgetByContractId(@Param("contractId") Long contractId);

    @Query("SELECT SUM(r.releasedAmount) FROM RestorationTask r WHERE r.contractId = :contractId")
    Double getTotalReleasedByContractId(@Param("contractId") Long contractId);

    @Query("SELECT r FROM RestorationTask r WHERE r.taskType = :taskType AND r.taskStatus = 'PENDING' ORDER BY r.createdAt DESC")
    List<RestorationTask> findPendingByTaskType(@Param("taskType") com.ecovoice.domain.RestorationTaskType taskType);

    List<RestorationTask> findByContractorWallet(String contractorWallet);
}