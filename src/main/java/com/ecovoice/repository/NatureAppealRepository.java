package com.ecovoice.repository;

import com.ecovoice.domain.AppealType;
import com.ecovoice.domain.BountyStatus;
import com.ecovoice.domain.NatureAppeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NatureAppealRepository extends JpaRepository<NatureAppeal, Long> {

    List<NatureAppeal> findByEntityIdAndActiveTrueOrderByCreatedAtDesc(Long entityId);

    Optional<NatureAppeal> findFirstByEntityIdAndAppealTypeAndActiveTrue(Long entityId, AppealType appealType);

    long countByEntityIdAndActiveTrue(Long entityId);

    long countByActiveTrue();

    List<NatureAppeal> findByBountyStatusOrderByCreatedAtDesc(BountyStatus bountyStatus);

    List<NatureAppeal> findByClaimantNodeIdOrderByClaimedAtDesc(Long claimantNodeId);

    List<NatureAppeal> findByBountyStatusInOrderByCreatedAtDesc(List<BountyStatus> statuses);
}
