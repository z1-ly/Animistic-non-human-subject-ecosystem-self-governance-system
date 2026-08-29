package com.ecovoice.repository;

import com.ecovoice.domain.EntityIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EntityIdentityRepository extends JpaRepository<EntityIdentity, Long> {

    Optional<EntityIdentity> findByWalletAddress(String walletAddress);

    Optional<EntityIdentity> findByDid(String did);
}
