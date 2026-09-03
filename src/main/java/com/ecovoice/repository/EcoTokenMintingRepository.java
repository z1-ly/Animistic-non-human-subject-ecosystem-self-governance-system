package com.ecovoice.repository;

import com.ecovoice.domain.EcoTokenMinting;
import com.ecovoice.domain.EcoTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EcoTokenMintingRepository extends JpaRepository<EcoTokenMinting, Long> {

    List<EcoTokenMinting> findByContractId(Long contractId);

    List<EcoTokenMinting> findByEntityId(Long entityId);

    List<EcoTokenMinting> findByTokenType(EcoTokenType tokenType);

    @Query("SELECT SUM(e.ecoAmountMinted) FROM EcoTokenMinting e WHERE e.contractId = :contractId")
    Double getTotalMintedByContractId(@Param("contractId") Long contractId);

    @Query("SELECT e FROM EcoTokenMinting e WHERE e.contractId = :contractId AND e.tokenType = :tokenType")
    EcoTokenMinting findByContractIdAndTokenType(@Param("contractId") Long contractId, @Param("tokenType") EcoTokenType tokenType);
}