package com.ecovoice.dto;

import com.ecovoice.domain.AppealType;
import com.ecovoice.domain.BountyStatus;
import com.ecovoice.domain.NatureAppeal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NatureAppealDto {

    private Long id;
    private Long entityId;
    private AppealType appealType;
    private String appealLabel;
    private String message;
    private String actionDetail;
    private String actionParameters;
    private String measurableTarget;
    private String acceptanceCriteria;
    private String baselineData;
    private String rewardRules;
    private String triggerRule;
    private String severity;
    private LocalDateTime deadline;
    private Double bountyAmount;
    private BountyStatus bountyStatus;
    private String bountyStatusLabel;
    private String contractAddress;
    private String contractTxHash;
    private Long claimantNodeId;
    private String claimantName;
    private LocalDateTime claimedAt;
    private String fulfillerName;
    private LocalDateTime fulfilledAt;
    private boolean active;
    private LocalDateTime createdAt;

    public static NatureAppealDto from(NatureAppeal appeal) {
        return from(appeal, null);
    }

    public static NatureAppealDto from(NatureAppeal appeal, String claimantName) {
        return NatureAppealDto.builder()
                .id(appeal.getId())
                .entityId(appeal.getEntityId())
                .appealType(appeal.getAppealType())
                .appealLabel(appeal.getAppealType().getLabel())
                .message(appeal.getMessage())
                .actionDetail(appeal.getActionDetail())
                .actionParameters(appeal.getActionParameters())
                .measurableTarget(appeal.getMeasurableTarget())
                .acceptanceCriteria(appeal.getAcceptanceCriteria())
                .baselineData(appeal.getBaselineData())
                .rewardRules(appeal.getRewardRules())
                .triggerRule(appeal.getTriggerRule())
                .severity(appeal.getSeverity())
                .deadline(appeal.getDeadline())
                .bountyAmount(appeal.getBountyAmount())
                .bountyStatus(appeal.getBountyStatus())
                .bountyStatusLabel(appeal.getBountyStatus() != null
                        ? appeal.getBountyStatus().getLabel() : null)
                .contractAddress(appeal.getContractAddress())
                .contractTxHash(appeal.getContractTxHash())
                .claimantNodeId(appeal.getClaimantNodeId())
                .claimantName(claimantName)
                .claimedAt(appeal.getClaimedAt())
                .fulfillerName(appeal.getFulfillerName())
                .fulfilledAt(appeal.getFulfilledAt())
                .active(appeal.isActive())
                .createdAt(appeal.getCreatedAt())
                .build();
    }
}
