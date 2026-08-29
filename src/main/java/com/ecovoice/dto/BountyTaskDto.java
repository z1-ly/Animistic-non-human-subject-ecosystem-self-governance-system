package com.ecovoice.dto;

import com.ecovoice.domain.BountyStatus;
import com.ecovoice.domain.NatureAppeal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BountyTaskDto {

    private Long appealId;
    private Long entityId;
    private String entityName;
    private String entityCategory;
    private String appealLabel;
    private String message;
    private String actionDetail;
    private String actionParameters;
    private String measurableTarget;
    private String acceptanceCriteria;
    private String baselineData;
    private String rewardRules;
    private String severity;
    private LocalDateTime deadline;
    private Double bountyAmount;
    private BountyStatus bountyStatus;
    private String bountyStatusLabel;
    private String contractAddress;
    private Long claimantNodeId;
    private String claimantName;
    private LocalDateTime createdAt;

    public static BountyTaskDto from(NatureAppeal appeal, String entityName, String entityCategory,
                                     String claimantName) {
        return BountyTaskDto.builder()
                .appealId(appeal.getId())
                .entityId(appeal.getEntityId())
                .entityName(entityName)
                .entityCategory(entityCategory)
                .appealLabel(appeal.getAppealType().getLabel())
                .message(appeal.getMessage())
                .actionDetail(appeal.getActionDetail())
                .actionParameters(appeal.getActionParameters())
                .measurableTarget(appeal.getMeasurableTarget())
                .acceptanceCriteria(appeal.getAcceptanceCriteria())
                .baselineData(appeal.getBaselineData())
                .rewardRules(appeal.getRewardRules())
                .severity(appeal.getSeverity())
                .deadline(appeal.getDeadline())
                .bountyAmount(appeal.getBountyAmount())
                .bountyStatus(appeal.getBountyStatus())
                .bountyStatusLabel(appeal.getBountyStatus() != null
                        ? appeal.getBountyStatus().getLabel() : null)
                .contractAddress(appeal.getContractAddress())
                .claimantNodeId(appeal.getClaimantNodeId())
                .claimantName(claimantName)
                .createdAt(appeal.getCreatedAt())
                .build();
    }
}
