package com.ecovoice.dto;

import java.util.List;

/**
 * 任务评估请求
 */
public record EvaluationRequest(
        Long humanNodeId,
        Double completionPercentage,  // 完成百分比 (0-100)
        Double verificationScore,     // AI核验评分 (0-100)
        String evaluationReport,      // 评估报告
        List<String> penaltyFactors   // 惩罚因素（如覆盖不足、延迟完成等）
) {}