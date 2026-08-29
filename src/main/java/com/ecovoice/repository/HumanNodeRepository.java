package com.ecovoice.repository;

import com.ecovoice.domain.BountyStatus;
import com.ecovoice.domain.HumanNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HumanNodeRepository extends JpaRepository<HumanNode, Long> {

    Optional<HumanNode> findByDid(String did);

    Optional<HumanNode> findByWalletAddress(String walletAddress);

    List<HumanNode> findAllByOrderByCreatedAtDesc();
}
