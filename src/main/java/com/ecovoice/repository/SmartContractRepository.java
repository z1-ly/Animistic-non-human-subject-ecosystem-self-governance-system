package com.ecovoice.repository;

import com.ecovoice.domain.SmartContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SmartContractRepository extends JpaRepository<SmartContract, Long> {

    /**
     * 根据合约地址查找
     */
    Optional<SmartContract> findByContractAddress(String contractAddress);

    /**
     * 查找主合约
     */
    Optional<SmartContract> findByContractName(String contractName);
}