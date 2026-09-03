package com.ecovoice.repository;

import com.ecovoice.domain.NaturalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NaturalEntityRepository extends JpaRepository<NaturalEntity, Long> {
}
