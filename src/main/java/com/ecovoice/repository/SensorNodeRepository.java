package com.ecovoice.repository;

import com.ecovoice.domain.SensorNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SensorNodeRepository extends JpaRepository<SensorNode, Long> {

    List<SensorNode> findByEntityId(Long entityId);

    long countByEntityId(Long entityId);
}
