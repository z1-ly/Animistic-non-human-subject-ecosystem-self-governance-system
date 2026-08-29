package com.ecovoice.service;

import com.ecovoice.domain.*;
import com.ecovoice.dto.EnterpriseTaskDto;
import com.ecovoice.dto.EcologicalValueDto;
import com.ecovoice.dto.EvaluationRequest;
import com.ecovoice.dto.ResourceExchangeRequest;
import com.ecovoice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 企业生态流通服务
 * 整合：任务评估 → 生态币发放 → 智能合约还款 → 资源兑换
 */
@Service
@RequiredArgsConstructor
public class EnterpriseFlowService {

    private final NatureAppealRepository appealRepository;
    private final HumanNodeRepository humanNodeRepository;
    private final EntityIdentityRepository identityRepository;
    private final SmartContractRepository smartContractRepository;
    private final NaturalEntityRepository entityRepository;
    private final RestorationTaskRepository restorationTaskRepository;
    private final BlockchainLedgerService ledgerService;
    private final EcologicalValueCatalogService valueCatalogService;
    private final VitalRecordService vitalRecordService;

    /**
     * 获取企业已接取的任务列表（含评估状态）
     */
    @Transactional(readOnly = true)
    public List<EnterpriseTaskDto> getEnterpriseTasks(Long humanNodeId) {
        HumanNode human = humanNodeRepository.findById(humanNodeId)
                .orElseThrow(() -> new IllegalArgumentException("企业节点不存在"));

        List<NatureAppeal> appeals = appealRepository.findByClaimantNodeIdOrderByClaimedAtDesc(humanNodeId);
        List<EnterpriseTaskDto> result = new ArrayList<>();

        for (NatureAppeal appeal : appeals) {
            NaturalEntity entity = entityRepository.findById(appeal.getEntityId()).orElse(null);
            RestorationTask task = restorationTaskRepository.findByContractorWallet(human.getWalletAddress())
                    .stream()
                    .filter(t -> t.getContractId().equals(appeal.getId()))
                    .findFirst()
                    .orElse(null);

            EnterpriseTaskDto dto = EnterpriseTaskDto.from(
                    appeal,
                    entity != null ? entity.getName() : "未知主体",
                    entity != null ? entity.getCategory() : "",
                    human.getDisplayName(),
                    task
            );
            result.add(dto);
        }
        return result;
    }

    /**
     * 自动化评估任务完成情况并发放生态币
     * 基于传感器实时数据对比基线数据和验收标准进行评估
     */
    @Transactional
    public Map<String, Object> evaluateAndReward(Long appealId) {
        NatureAppeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("诉求不存在"));
        if (appeal.getBountyStatus() != BountyStatus.FULFILLED && 
            appeal.getBountyStatus() != BountyStatus.CLAIMED &&
            appeal.getBountyStatus() != BountyStatus.PAID) {
            throw new IllegalStateException("任务状态不允许评估");
        }
        if (appeal.getClaimantNodeId() == null) {
            throw new IllegalStateException("任务无接取者");
        }

        HumanNode contractor = humanNodeRepository.findById(appeal.getClaimantNodeId())
                .orElseThrow(() -> new IllegalArgumentException("企业节点不存在"));
        EntityIdentity entityIdentity = identityRepository.findById(appeal.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("主体钱包不存在"));
        SmartContract smartContract = smartContractRepository.findByContractName("主生态币资金池")
                .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));

        // 如果已评估过，先退还之前的奖励
        if (appeal.getBountyStatus() == BountyStatus.PAID) {
            restorationTaskRepository.findByContractorWallet(contractor.getWalletAddress())
                    .stream()
                    .filter(t -> t.getContractId().equals(appealId))
                    .findFirst()
                    .ifPresent(task -> {
                        if (task.getReleasedAmount() != null && task.getReleasedAmount() > 0) {
                            double prevReward = task.getReleasedAmount();
                            contractor.setWalletBalance(Math.max(0, contractor.getWalletBalance() - prevReward));
                            contractor.setTotalEarned(Math.max(0, contractor.getTotalEarned() - prevReward));
                            humanNodeRepository.save(contractor);
                            
                            entityIdentity.setLockedBalance(entityIdentity.getLockedBalance() + prevReward);
                            identityRepository.save(entityIdentity);
                        }
                    });
        }

        // 自动评估：获取传感器数据，计算改进比率和AI评分
        EvaluationResult evalResult = autoEvaluate(appeal);

        // 核算生态币奖励：最终奖励不超过悬赏金额，评估效果越好获得越多，支持扣减惩罚
        double baseReward = appeal.getBountyAmount() != null ? appeal.getBountyAmount() : 0;
        double completionRatio = evalResult.completionPercentage / 100.0;
        double penalty = calculatePenalty(appeal, evalResult);
        double finalReward = Math.max(0, baseReward * completionRatio - penalty);

        // 修复任务状态更新
        restorationTaskRepository.findByContractorWallet(contractor.getWalletAddress())
                .stream()
                .filter(t -> t.getContractId().equals(appealId))
                .findFirst()
                .ifPresent(task -> {
                    task.setTaskStatus(TaskStatus.VERIFIED);
                    task.setAiVerificationScore(evalResult.aiScore);
                    task.setVerificationReport(evalResult.report);
                    task.setReleasedAmount(finalReward);
                    task.setVerifiedAt(LocalDateTime.now());
                    task.setCompletionPercentage(evalResult.completionPercentage);
                    restorationTaskRepository.save(task);
                });

        // 发放生态币给企业
        if (finalReward > 0) {
            entityIdentity.setLockedBalance(Math.max(0, entityIdentity.getLockedBalance() - baseReward));
            entityIdentity.setWalletBalance(Math.max(0, entityIdentity.getWalletBalance() - baseReward));

            contractor.setWalletBalance(contractor.getWalletBalance() + finalReward);
            contractor.setTotalEarned(contractor.getTotalEarned() + finalReward);
            humanNodeRepository.save(contractor);

            ledgerService.appendTransaction(
                    LedgerTransactionType.BOUNTY_PAYOUT,
                    appeal.getEntityId(), appealId,
                    entityIdentity.getWalletAddress(),
                    contractor.getWalletAddress(),
                    finalReward,
                    "任务评估结算 · " + appeal.getAppealType().getLabel()
                            + " · 悬赏金额：" + baseReward + " ECO"
                            + " · 扣减惩罚：" + penalty + " ECO"
                            + " · 最终发放：" + finalReward + " ECO"
                            + " · 完成率：" + evalResult.completionPercentage + "%"
                            + " · AI评分：" + evalResult.aiScore
            );
        }

        appeal.setBountyStatus(BountyStatus.PAID);
        appealRepository.save(appeal);

        // 自动还款：评估完成后立即将借币归还智能合约
        Map<String, Object> repayResult = repayToSmartContract(appealId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appealId", appealId);
        result.put("appealType", appeal.getAppealType().getLabel());
        result.put("baseReward", baseReward);
        result.put("bonusReward", 0);
        result.put("penalty", penalty);
        result.put("finalReward", finalReward);
        result.put("completionPercentage", evalResult.completionPercentage);
        result.put("verificationScore", evalResult.aiScore);
        result.put("evaluationReport", evalResult.report);
        result.put("sensorMetrics", evalResult.sensorMetrics);
        result.put("contractorBalance", contractor.getWalletBalance());
        result.put("evaluatedAt", LocalDateTime.now().toString());
        
        // 添加自动还款信息
        result.put("autoRepay", true);
        result.put("repayAmount", repayResult.get("repayAmount"));
        result.put("smartContractAvailableAfter", repayResult.get("smartContractAvailableAfter"));
        return result;
    }

    /**
     * 任务结束后还款给智能合约
     * 非人类主体向智能合约借了多少就还多少（全额还款）
     */
    @Transactional
    public Map<String, Object> repayToSmartContract(Long appealId) {
        NatureAppeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("诉求不存在"));
        if (appeal.getBountyStatus() != BountyStatus.PAID) {
            throw new IllegalStateException("任务尚未完成支付，无法还款");
        }

        EntityIdentity entityIdentity = identityRepository.findById(appeal.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("主体钱包不存在"));
        SmartContract smartContract = smartContractRepository.findByContractName("主生态币资金池")
                .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));

        // 全额还款：借了多少就还多少
        double borrowedAmount = appeal.getBountyAmount() != null ? appeal.getBountyAmount() : 0;
        double repayAmount = borrowedAmount;

        // 从主体钱包还款给智能合约
        if (repayAmount > 0) {
            entityIdentity.setWalletBalance(Math.max(0, entityIdentity.getWalletBalance() - repayAmount));
            entityIdentity.setBorrowedBalance(Math.max(0, entityIdentity.getBorrowedBalance() - borrowedAmount));
            identityRepository.save(entityIdentity);

            smartContract.recover(repayAmount);
            smartContractRepository.save(smartContract);
        }

        // 记录交易
        ledgerService.appendTransaction(
                LedgerTransactionType.REPAY,
                appeal.getEntityId(), appealId,
                entityIdentity.getWalletAddress(),
                smartContract.getContractAddress(),
                repayAmount,
                "智能合约还款 · 全额归还：" + repayAmount + " ECO"
                        + " · 任务：" + appeal.getAppealType().getLabel()
        );

        // 更新诉求状态为已结清
        appeal.setBountyStatus(BountyStatus.SETTLED);
        appeal.setActive(false);
        appealRepository.save(appeal);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appealId", appealId);
        result.put("borrowedAmount", borrowedAmount);
        result.put("repayAmount", repayAmount);
        result.put("entityBalanceAfter", entityIdentity.getWalletBalance());
        result.put("smartContractAvailableAfter", smartContract.getAvailableBalance());
        result.put("settledAt", LocalDateTime.now().toString());
        return result;
    }

    /**
     * 企业使用生态币兑换资源
     */
    @Transactional
    public Map<String, Object> exchangeResource(Long humanNodeId, ResourceExchangeRequest request) {
        HumanNode human = humanNodeRepository.findById(humanNodeId)
                .orElseThrow(() -> new IllegalArgumentException("企业节点不存在"));

        EcologicalValueDto valueType = valueCatalogService.getByKey(request.getValueTypeKey());
        double amount = valueType.getEcoPricePerUnit() * request.getQuantity();

        if (human.getWalletBalance() < amount) {
            throw new IllegalStateException("生态币余额不足：需 " + amount + " ECO，当前 " + human.getWalletBalance() + " ECO");
        }

        // 扣除企业生态币
        human.setWalletBalance(human.getWalletBalance() - amount);
        human.setTotalSpent(human.getTotalSpent() + amount);
        humanNodeRepository.save(human);

        // 根据回流逻辑处理资金流向
        String refluxTarget = valueType.getRefluxLogic();
        if ("SMART_CONTRACT".equals(refluxTarget)) {
            // 回流到智能合约
            SmartContract smartContract = smartContractRepository.findByContractName("主生态币资金池")
                    .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));
            smartContract.recover(amount);
            smartContractRepository.save(smartContract);

            ledgerService.appendTransaction(
                    LedgerTransactionType.ECOLOGICAL_FEE,
                    request.getTargetEntityId() != null ? request.getTargetEntityId() : 0L,
                    null,
                    human.getWalletAddress(),
                    smartContract.getContractAddress(),
                    amount,
                    "资源兑换 · " + valueType.getMajorCategory()
                            + " · " + valueType.getCoreValueIndicator()
                            + " · 数量：" + request.getQuantity() + " " + valueType.getUnit()
                            + " · 金额：" + amount + " ECO"
                            + " · 回流：智能合约资金池"
            );
        } else if (request.getTargetEntityId() != null) {
            // 回流到自然主体国库
            EntityIdentity targetEntity = identityRepository.findById(request.getTargetEntityId())
                    .orElseThrow(() -> new IllegalArgumentException("目标自然主体不存在"));
            targetEntity.setWalletBalance(targetEntity.getWalletBalance() + amount);
            targetEntity.setTotalEarned(targetEntity.getTotalEarned() + amount);
            identityRepository.save(targetEntity);

            ledgerService.appendTransaction(
                    LedgerTransactionType.ECOLOGICAL_FEE,
                    request.getTargetEntityId(), null,
                    human.getWalletAddress(),
                    targetEntity.getWalletAddress(),
                    amount,
                    "资源兑换 · " + valueType.getMajorCategory()
                            + " · " + valueType.getCoreValueIndicator()
                            + " · 数量：" + request.getQuantity() + " " + valueType.getUnit()
                            + " · 金额：" + amount + " ECO"
                            + " · 回流：" + entityName(request.getTargetEntityId()) + " 国库"
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valueTypeKey", request.getValueTypeKey());
        result.put("majorCategory", valueType.getMajorCategory());
        result.put("coreValueIndicator", valueType.getCoreValueIndicator());
        result.put("quantity", request.getQuantity());
        result.put("unit", valueType.getUnit());
        result.put("pricePerUnit", valueType.getEcoPricePerUnit());
        result.put("totalAmount", amount);
        result.put("humanBalanceAfter", human.getWalletBalance());
        result.put("exchangedAt", LocalDateTime.now().toString());
        return result;
    }

    /**
     * 获取企业可兑换的资源目录
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getResourceCatalog() {
        return valueCatalogService.listAll().stream()
                .map(v -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("valueTypeKey", v.getValueTypeKey());
                    item.put("majorCategory", v.getMajorCategory());
                    item.put("coreValueIndicator", v.getCoreValueIndicator());
                    item.put("unit", v.getUnit());
                    item.put("ecoPricePerUnit", v.getEcoPricePerUnit());
                    item.put("refluxLogic", v.getRefluxLogic());
                    return item;
                })
                .toList();
    }

    /**
     * 计算扣减惩罚
     */
    private double calculatePenalty(NatureAppeal appeal, EvaluationResult result) {
        if (result.completionPercentage < 80) {
            double base = appeal.getBountyAmount() != null ? appeal.getBountyAmount() : 0;
            double penaltyRate = (100 - result.completionPercentage) * 0.01;
            return base * penaltyRate;
        }

        String rewardRules = appeal.getRewardRules();
        if (rewardRules == null || !rewardRules.contains("扣减")) {
            return 0;
        }

        if (result.penaltyFactors != null && !result.penaltyFactors.isEmpty()) {
            double base = appeal.getBountyAmount() != null ? appeal.getBountyAmount() : 0;
            return base * 0.15;
        }
        return 0;
    }

    /**
     * 自动化评估核心逻辑
     * 基于传感器实时数据对比基线数据和验收标准进行评估
     */
    private EvaluationResult autoEvaluate(NatureAppeal appeal) {
        List<Map<String, Object>> sensorMetrics = new ArrayList<>();
        List<String> penaltyFactors = new ArrayList<>();
        double aiScore;
        double completionPercentage;
        StringBuilder report = new StringBuilder();
        Map<SensorType, Double> currentAverages = new HashMap<>();

        LocalDateTime since = LocalDateTime.now().minusHours(24);
        try {
            List<VitalSignRecord> recentRecords = vitalRecordService
                    .findByEntityIdAndRecordedAtAfter(appeal.getEntityId(), since);

            if (recentRecords.isEmpty()) {
                currentAverages.clear();
                currentAverages.putAll(generateSimulatedData(appeal));
                report.append("(使用模拟数据进行评估，真实传感器数据不足)\n");
            } else {
                recentRecords.stream()
                        .collect(java.util.stream.Collectors.groupingBy(VitalSignRecord::getSensorType))
                        .forEach((type, records) -> {
                            double sum = records.stream().mapToDouble(VitalSignRecord::getValue).sum();
                            currentAverages.put(type, sum / records.size());
                        });
            }
        } catch (Exception e) {
            currentAverages.clear();
            currentAverages.putAll(generateSimulatedData(appeal));
            report.append("(传感器数据查询异常，使用模拟数据进行评估)\n");
        }

        Map<String, Double> baselineValues = parseBaselineData(appeal.getBaselineData());
        Map<String, Double[]> acceptanceRanges = parseAcceptanceCriteria(appeal.getAcceptanceCriteria());

        double totalImprovement = 0;
        int metricCount = 0;

        for (Map.Entry<SensorType, Double> entry : currentAverages.entrySet()) {
            SensorType type = entry.getKey();
            double currentValue = entry.getValue();
            double baselineValue = baselineValues.getOrDefault(type.name(), type.getDefaultValue());
            if (baselineValue == currentValue || Math.abs(baselineValue - currentValue) < 0.01) {
                if (currentValue < type.getNormalMin()) {
                    baselineValue = currentValue * 0.8;
                } else if (currentValue > type.getNormalMax()) {
                    baselineValue = currentValue * 1.2;
                } else {
                    double range = type.getNormalMax() - type.getNormalMin();
                    if (currentValue < type.getNormalMin() + range * 0.3) {
                        baselineValue = type.getNormalMin() - range * 0.2;
                    } else if (currentValue > type.getNormalMax() - range * 0.3) {
                        baselineValue = type.getNormalMax() + range * 0.2;
                    } else {
                        baselineValue = type.getNormalMin() + range * 0.1;
                    }
                }
            }

            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("sensorType", type.name());
            metric.put("sensorLabel", type.getLabel());
            metric.put("unit", type.getUnit());
            metric.put("baselineValue", baselineValue);
            metric.put("currentValue", currentValue);

            double improvement = 0;
            double normalMid = (type.getNormalMin() + type.getNormalMax()) / 2;

            if (baselineValue < type.getNormalMin()) {
                improvement = Math.min(1.0, (currentValue - baselineValue) / (type.getNormalMin() - baselineValue));
            } else if (baselineValue > type.getNormalMax()) {
                improvement = Math.min(1.0, (baselineValue - currentValue) / (baselineValue - type.getNormalMax()));
            } else {
                double distanceToNormal = Math.abs(currentValue - normalMid);
                double baselineDistance = Math.abs(baselineValue - normalMid);
                if (baselineDistance > 0) {
                    improvement = Math.max(0, 1 - distanceToNormal / baselineDistance);
                } else {
                    improvement = 1.0;
                }
            }

            improvement = Math.max(0, Math.min(1, improvement));
            metric.put("improvementRatio", improvement);

            Double[] range = acceptanceRanges.get(type.name());
            boolean meetsCriteria = range == null ||
                    (currentValue >= range[0] && currentValue <= range[1]);
            metric.put("meetsCriteria", meetsCriteria);

            if (!meetsCriteria) {
                penaltyFactors.add(type.getLabel() + "未达标");
            }

            totalImprovement += improvement;
            metricCount++;
            sensorMetrics.add(metric);
        }

        completionPercentage = metricCount > 0 ? Math.round(totalImprovement / metricCount * 100) : 50;
        aiScore = calculateAiScore(completionPercentage, penaltyFactors.isEmpty(), sensorMetrics);

        report.append("【AI自动化评估报告】\n");
        report.append("评估时间：").append(LocalDateTime.now()).append("\n");
        report.append("完成率：").append(completionPercentage).append("%\n");
        report.append("AI评分：").append(String.format("%.2f", aiScore)).append("\n");
        report.append("传感器指标详情：\n");
        for (Map<String, Object> metric : sensorMetrics) {
            report.append("  - ").append(metric.get("sensorLabel")).append(": ");
            report.append("基线=").append(metric.get("baselineValue"));
            report.append(", 当前=").append(metric.get("currentValue"));
            report.append(", 改进率=").append(String.format("%.2f", (Double) metric.get("improvementRatio")));
            report.append(", 达标=").append(metric.get("meetsCriteria")).append("\n");
        }
        if (!penaltyFactors.isEmpty()) {
            report.append("扣减因素：").append(String.join("; ", penaltyFactors)).append("\n");
        }

        return new EvaluationResult(aiScore, completionPercentage, report.toString(), sensorMetrics, penaltyFactors);
    }

    private double calculateAiScore(double completionPercentage, boolean meetsAllCriteria,
                                    List<Map<String, Object>> sensorMetrics) {
        double score = completionPercentage;
        if (meetsAllCriteria) {
            score += 10;
        }
        int excellentCount = (int) sensorMetrics.stream()
                .filter(m -> (Double) m.get("improvementRatio") >= 0.8)
                .count();
        score += excellentCount * 2;
        score = Math.min(100, Math.max(0, score));
        return Math.round(score * 100.0) / 100.0;
    }

    private Map<String, Double> parseBaselineData(String baselineData) {
        Map<String, Double> result = new HashMap<>();
        if (baselineData == null || baselineData.isEmpty()) {
            return result;
        }
        try {
            String[] parts = baselineData.split("[，,。]");
            for (String part : parts) {
                part = part.trim();
                for (SensorType type : SensorType.values()) {
                    if (part.contains(type.getLabel())) {
                        String numStr = part.replaceAll("[^\\d.]", "");
                        if (!numStr.isEmpty()) {
                            result.put(type.name(), Double.parseDouble(numStr));
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, Double[]> parseAcceptanceCriteria(String acceptanceCriteria) {
        Map<String, Double[]> result = new HashMap<>();
        if (acceptanceCriteria == null || acceptanceCriteria.isEmpty()) {
            return result;
        }
        try {
            for (SensorType type : SensorType.values()) {
                if (acceptanceCriteria.contains(type.getLabel())) {
                    result.put(type.name(), new Double[]{type.getNormalMin(), type.getNormalMax()});
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * 生成模拟传感器数据
     * 当没有真实传感器数据时，根据基线数据和诉求类型模拟修复后的改善值
     */
    private Map<SensorType, Double> generateSimulatedData(NatureAppeal appeal) {
        Map<SensorType, Double> simulatedData = new HashMap<>();
        Map<String, Double> baselineValues = parseBaselineData(appeal.getBaselineData());

        for (SensorType type : SensorType.values()) {
            double baseline = baselineValues.getOrDefault(type.name(), type.getDefaultValue());
            double normalMin = type.getNormalMin();
            double normalMax = type.getNormalMax();
            double normalMid = (normalMin + normalMax) / 2;

            double simulatedValue;
            if (baseline < normalMin) {
                simulatedValue = normalMin + (normalMid - normalMin) * 0.3;
            } else if (baseline > normalMax) {
                simulatedValue = normalMax - (normalMax - normalMid) * 0.3;
            } else {
                double distanceToMid = Math.abs(baseline - normalMid);
                if (distanceToMid < (normalMax - normalMin) * 0.1) {
                    simulatedValue = baseline;
                } else {
                    simulatedValue = normalMid + (baseline - normalMid) * 0.3;
                }
            }

            double variance = (normalMax - normalMin) * 0.05;
            simulatedValue = simulatedValue + (Math.random() - 0.5) * variance * 2;
            simulatedValue = Math.max(normalMin * 0.8, Math.min(normalMax * 1.2, simulatedValue));

            simulatedData.put(type, Math.round(simulatedValue * 100.0) / 100.0);
        }

        return simulatedData;
    }

    private record EvaluationResult(
            double aiScore,
            double completionPercentage,
            String report,
            List<Map<String, Object>> sensorMetrics,
            List<String> penaltyFactors
    ) {}

    private String entityName(Long entityId) {
        return entityRepository.findById(entityId)
                .map(NaturalEntity::getName)
                .orElse("未知主体");
    }
}