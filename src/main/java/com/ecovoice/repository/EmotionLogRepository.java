package com.ecovoice.repository;

import com.ecovoice.domain.EmotionLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmotionLogRepository extends JpaRepository<EmotionLog, Long> {

    List<EmotionLog> findByEntityIdOrderByCreatedAtDesc(Long entityId, Pageable pageable);

    Optional<EmotionLog> findFirstByEntityIdOrderByCreatedAtDesc(Long entityId);
}
