package com.ecovoice.repository;

import com.ecovoice.domain.CrossEntityTransaction;
import com.ecovoice.domain.EcoServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CrossEntityTransactionRepository extends JpaRepository<CrossEntityTransaction, Long> {

    List<CrossEntityTransaction> findByFromEntityId(Long fromEntityId);

    List<CrossEntityTransaction> findByToEntityId(Long toEntityId);

    List<CrossEntityTransaction> findByServiceType(EcoServiceType serviceType);

    List<CrossEntityTransaction> findByIsRecurringTrueAndNextPaymentDateBefore(LocalDateTime date);

    @Query("SELECT SUM(c.ecoAmount) FROM CrossEntityTransaction c WHERE c.toEntityId = :entityId AND c.createdAt >= :startDate AND c.createdAt <= :endDate")
    Double getTotalReceivedByEntityIdAndPeriod(@Param("entityId") Long entityId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(c.ecoAmount) FROM CrossEntityTransaction c WHERE c.fromEntityId = :entityId AND c.createdAt >= :startDate AND c.createdAt <= :endDate")
    Double getTotalPaidByEntityIdAndPeriod(@Param("entityId") Long entityId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}