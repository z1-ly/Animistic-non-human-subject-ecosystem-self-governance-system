package com.ecovoice.repository;

import com.ecovoice.domain.NetworkConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NetworkConfigRepository extends JpaRepository<NetworkConfig, Long> {

    Optional<NetworkConfig> findByEntityId(Long entityId);
}
