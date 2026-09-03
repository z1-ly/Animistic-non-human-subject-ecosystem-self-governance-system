package com.ecovoice.dto;

import com.ecovoice.domain.*;

import java.time.LocalDateTime;

/**
 * 企业任务DTO - 包含诉求和修复任务信息
 */
public record EnterpriseTaskDto(
        Long appealId,
        Long entityId,
        String entityName,
        String entityCategory,
        AppealType appealType,
        String appealLabel,
        String message,
        String actionDetail,
        String actionParameters,
        String acceptanceCriteria,
        String measurableTarget,
        String baselineData,
        String rewardRules,
        Double bountyAmount,
        BountyStatus bountyStatus,
        String bountyStatusLabel,
        LocalDateTime deadline,
        LocalDateTime claimedAt,
        LocalDateTime fulfilledAt,
        String claimantName,
        // 修复任务相关
        Long taskId,
        RestorationTaskType taskType,
        String taskName,
        TaskStatus taskStatus,
        Double completionPercentage,
        Double aiVerificationScore,
        Double releasedAmount,
        String verificationReport,
        LocalDateTime verifiedAt
) {
    public static EnterpriseTaskDto from(NatureAppeal appeal, String entityName, String entityCategory,
                                          String claimantName, RestorationTask task) {
        return new EnterpriseTaskDto(
                appeal.getId(),
                appeal.getEntityId(),
                entityName,
                entityCategory,
                appeal.getAppealType(),
                appeal.getAppealType() != null ? appeal.getAppealType().getLabel() : null,
                appeal.getMessage(),
                appeal.getActionDetail(),
                appeal.getActionParameters(),
                appeal.getAcceptanceCriteria(),
                appeal.getMeasurableTarget(),
                appeal.getBaselineData(),
                appeal.getRewardRules(),
                appeal.getBountyAmount(),
                appeal.getBountyStatus(),
                appeal.getBountyStatus() != null ? appeal.getBountyStatus().getLabel() : null,
                appeal.getDeadline(),
                appeal.getClaimedAt(),
                appeal.getFulfilledAt(),
                claimantName,
                task != null ? task.getId() : null,
                task != null ? task.getTaskType() : null,
                task != null ? task.getTaskName() : null,
                task != null ? task.getTaskStatus() : null,
                task != null ? task.getCompletionPercentage() : null,
                task != null ? task.getAiVerificationScore() : null,
                task != null ? task.getReleasedAmount() : null,
                task != null ? task.getVerificationReport() : null,
                task != null ? task.getVerifiedAt() : null
        );
    }
}