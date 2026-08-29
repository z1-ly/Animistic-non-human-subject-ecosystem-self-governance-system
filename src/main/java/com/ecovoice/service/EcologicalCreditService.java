package com.ecovoice.service;

import com.ecovoice.domain.*;
import com.ecovoice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生态借贷服务
 * 简化版：自然主体向智能合约借贷生态币用于启动生态修复
 * 智能合约作为唯一的生态币资金池，恒定总量
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EcologicalCreditService {

    private final EcologicalCreditContractRepository contractRepository;
    private final EntityIdentityRepository identityRepository;
    private final NaturalEntityRepository entityRepository;
    private final VitalRecordService vitalRecordService;
    private final SmartContractRepository smartContractRepository;
    private final EcoCoinService ecoCoinService;
    private final BlockchainLedgerService ledgerService;
    private final EcoCoinLoanService ecoCoinLoanService;

    // 默认修复周期配置
    private static final int DEFAULT_REPAIR_DAYS = 90;  // 3个月修复期
    private static final int DEFAULT_MONITORING_DAYS = 90;  // 3个月监测期
    private static final int TOTAL_DAYS = DEFAULT_REPAIR_DAYS + DEFAULT_MONITORING_DAYS;  // 总共6个月

    /**
     * AI评估生态健康状况并自动触发借贷申请
     * 当监测数据连续超标、生态功能持续崩溃时自动触发
     */
    @Transactional
    public EcologicalCreditContract autoTriggerCreditApplication(Long entityId) {
        NaturalEntity entity = entityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("自然主体不存在"));

        EntityIdentity identity = identityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("主体钱包未初始化"));

        // AI评估：获取最近180天的监测数据
        LocalDateTime startDate = LocalDateTime.now().minusDays(180);
        List<VitalSignRecord> recentRecords = vitalRecordService
                .findByEntityIdAndRecordedAtAfter(entityId, startDate);

        if (recentRecords.isEmpty()) {
            throw new IllegalStateException("监测数据不足，无法进行AI评估");
        }

        // AI评估报告生成
        Map<String, Object> aiAssessment = performAiAssessment(entity, recentRecords);

        // 检查是否达到借贷触发条件
        boolean shouldTrigger = checkCreditTriggerConditions(aiAssessment);
        if (!shouldTrigger) {
            throw new IllegalStateException("未达到借贷触发条件，生态状态尚可");
        }

        // 计算预期生态增量
        Map<String, Double> estimatedGains = calculateEstimatedGains(entity, aiAssessment);

        // 计算借贷金额
        double creditAmount = calculateCreditAmount(estimatedGains);

        // 获取智能合约
        SmartContract smartContract = smartContractRepository.findByContractName("主生态币资金池")
                .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));

        // 检查智能合约可用余额
        if (smartContract.getAvailableBalance() < creditAmount) {
            throw new IllegalStateException("智能合约可用余额不足，无法借贷");
        }

        // 生成合约地址
        String contractAddress = generateContractAddress();

        // 创建借贷合约
        EcologicalCreditContract contract = EcologicalCreditContract.builder()
                .contractAddress(contractAddress)
                .entityId(entityId)
                .entityName(entity.getName())
                .creditAmount(creditAmount)
                .ecoTokenLocked(creditAmount)  // 借贷金额
                .estimatedCarbonSink(estimatedGains.getOrDefault("carbonSink", 0.0))
                .estimatedWaterPurification(estimatedGains.getOrDefault("waterPurification", 0.0))
                .estimatedWaterConservation(estimatedGains.getOrDefault("waterConservation", 0.0))
                .estimatedBiodiversity(estimatedGains.getOrDefault("biodiversity", 0.0))
                .estimatedClimateRegulation(estimatedGains.getOrDefault("climateRegulation", 0.0))
                .repairBudget(creditAmount)
                .repairDays(DEFAULT_REPAIR_DAYS)
                .monitoringDays(DEFAULT_MONITORING_DAYS)
                .totalDays(TOTAL_DAYS)
                .contractStatus(CreditContractStatus.PENDING)
                .applicationReason("AI自动触发：基于180天监测数据分析，识别出生态功能持续崩溃")
                .aiAssessmentReport(buildAiAssessmentReport(aiAssessment))
                .baselineDataSnapshot(buildBaselineDataSnapshot(recentRecords))
                .repaymentDeadline(LocalDateTime.now().plusDays(TOTAL_DAYS))
                .build();

        contract = contractRepository.save(contract);

        // 记录区块链交易
        ledgerService.appendTransaction(
                LedgerTransactionType.CONTRACT_DEPLOY,
                entityId, contract.getId(),
                smartContract.getContractAddress(),
                contractAddress,
                0.0,
                "生态借贷合约部署 · " + entity.getName() + " · 借贷金额：" + creditAmount + " 生态币");

        log.info("生态借贷申请已自动触发：实体={}, 合约地址={}, 借贷金额={}",
                entity.getName(), contractAddress, creditAmount);

        return contract;
    }

    /**
     * 智能合约自动审批借贷申请
     * 从智能合约借出生态币给自然体用于修复
     */
    @Transactional
    public EcologicalCreditContract approveCreditContract(Long contractId) {
        EcologicalCreditContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

        if (contract.getContractStatus() != CreditContractStatus.PENDING) {
            throw new IllegalStateException("合约状态不允许审批");
        }

        EntityIdentity identity = identityRepository.findById(contract.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("主体钱包未初始化"));

        SmartContract smartContract = smartContractRepository.findByContractName("主生态币资金池")
                .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));

        if (smartContract.getAvailableBalance() < contract.getCreditAmount()) {
            throw new IllegalStateException("智能合约可用余额不足，无法借币");
        }

        // 使用生态币借贷服务借币
        ecoCoinLoanService.borrowCoins(
                contract.getEntityId(),
                contract.getCreditAmount(),
                "生态借贷合约 · " + entityRepository.findById(contract.getEntityId()).get().getName() + " · 生态修复"
        );

        // 更新主体钱包余额
        identity.setWalletBalance(identity.getWalletBalance() + contract.getCreditAmount());
        identityRepository.save(identity);

        contract.setContractStatus(CreditContractStatus.APPROVED);
        contractRepository.save(contract);

        log.info("生态借贷合约已批准：合约地址={}, 借贷金额={}",
                contract.getContractAddress(), contract.getCreditAmount());

        return contract;
    }

    /**
     * 更新合约状态为已募资
     */
    @Transactional
    public EcologicalCreditContract markAsFunded(Long contractId) {
        EcologicalCreditContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

        if (contract.getContractStatus() != CreditContractStatus.APPROVED) {
            throw new IllegalStateException("合约状态不允许标记为已募资");
        }

        contract.setContractStatus(CreditContractStatus.FUNDED);
        contractRepository.save(contract);

        log.info("生态借贷合约已募资完成：合约地址={}", contract.getContractAddress());
        return contract;
    }

    /**
     * 更新合约状态为修复中
     */
    @Transactional
    public EcologicalCreditContract markAsInRepair(Long contractId) {
        EcologicalCreditContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

        if (contract.getContractStatus() != CreditContractStatus.FUNDED) {
            throw new IllegalStateException("合约状态不允许开始修复");
        }

        contract.setContractStatus(CreditContractStatus.IN_REPAIR);
        contractRepository.save(contract);

        log.info("生态修复工程已启动：合约地址={}", contract.getContractAddress());
        return contract;
    }

    /**
     * 更新合约状态为监测验证中
     */
    @Transactional
    public EcologicalCreditContract markAsInMonitoring(Long contractId) {
        EcologicalCreditContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

        if (contract.getContractStatus() != CreditContractStatus.IN_REPAIR) {
            throw new IllegalStateException("合约状态不允许进入监测阶段");
        }

        contract.setContractStatus(CreditContractStatus.IN_MONITORING);
        contractRepository.save(contract);

        log.info("生态修复已进入监测验证阶段：合约地址={}", contract.getContractAddress());
        return contract;
    }

    /**
     * 记录实际生态增量并完成合约
     */
    @Transactional
    public EcologicalCreditContract completeContract(Long contractId, Map<String, Double> actualGains) {
        EcologicalCreditContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

        if (contract.getContractStatus() != CreditContractStatus.IN_MONITORING) {
            throw new IllegalStateException("合约状态不允许完成");
        }

        EntityIdentity identity = identityRepository.findById(contract.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("主体钱包未初始化"));

        SmartContract smartContract = smartContractRepository.findByContractName("主生态币资金池")
                .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));

        contract.setActualCarbonSink(actualGains.getOrDefault("carbonSink", 0.0));
        contract.setActualWaterPurification(actualGains.getOrDefault("waterPurification", 0.0));
        contract.setActualWaterConservation(actualGains.getOrDefault("waterConservation", 0.0));
        contract.setActualBiodiversity(actualGains.getOrDefault("biodiversity", 0.0));
        contract.setActualClimateRegulation(actualGains.getOrDefault("climateRegulation", 0.0));

        double totalEcoMinted = calculateTotalEcoMinted(actualGains);
        contract.setTotalEcoMinted(totalEcoMinted);

        double repaymentAmount = contract.getCreditAmount();
        contract.setRepaymentAmount(repaymentAmount);

        // 使用生态币借贷服务还币
        ecoCoinLoanService.repayCoins(
                contract.getEntityId(),
                repaymentAmount,
                "生态借贷合约完成 · " + entityRepository.findById(contract.getEntityId()).get().getName() + " · 归还借币"
        );

        // 更新主体钱包余额
        identity.setWalletBalance(identity.getWalletBalance() - repaymentAmount);
        identity.setTotalSpent(identity.getTotalSpent() + repaymentAmount);
        identityRepository.save(identity);

        contract.setContractStatus(CreditContractStatus.COMPLETED);
        contract.setCompletedAt(LocalDateTime.now());
        contract.setRepaymentStatus("COMPLETED");
        contractRepository.save(contract);

        log.info("生态借贷合约已完成：合约地址={}, 实际铸造生态币={}, 还款金额={}",
                contract.getContractAddress(), totalEcoMinted, repaymentAmount);

        return contract;
    }

    /**
     * 获取待处理的借贷合约
     */
    @Transactional(readOnly = true)
    public List<EcologicalCreditContract> getPendingContracts() {
        return contractRepository.findByContractStatus(CreditContractStatus.PENDING);
    }

    /**
     * 获取需要验证的合约（监测期结束）
     */
    @Transactional(readOnly = true)
    public List<EcologicalCreditContract> getContractsNeedingVerification() {
        LocalDateTime now = LocalDateTime.now();
        return contractRepository.findContractsNeedingVerification(now);
    }

    // ========== 私有辅助方法 ==========

    private Map<String, Object> performAiAssessment(NaturalEntity entity, List<VitalSignRecord> records) {
        Map<String, Object> assessment = new LinkedHashMap<>();

        // 计算各项指标的平均值和趋势
        Map<String, Double> avgValues = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getSensorType() != null ? r.getSensorType().name() : "UNKNOWN",
                        Collectors.averagingDouble(VitalSignRecord::getValue)
                ));

        // 识别超标天数
        long exceedDays = records.stream()
                .filter(r -> isValueExceedingThreshold(r))
                .count();

        // 计算生态健康评分
        double healthScore = calculateHealthScore(avgValues, exceedDays, records.size());

        assessment.put("entityName", entity.getName());
        assessment.put("entityCategory", entity.getCategory());
        assessment.put("monitoringPeriod", "180天");
        assessment.put("totalRecords", records.size());
        assessment.put("exceedDays", exceedDays);
        assessment.put("exceedRate", (double) exceedDays / records.size());
        assessment.put("healthScore", healthScore);
        assessment.put("avgValues", avgValues);
        assessment.put("assessmentTime", LocalDateTime.now());

        return assessment;
    }

    private boolean isValueExceedingThreshold(VitalSignRecord record) {
        // 简化的阈值判断逻辑，实际应根据不同传感器类型设置具体阈值
        double value = record.getValue();
        return value < 2.0 || value > 100.0;  // 示例：溶解氧低于2mg/L或超过100为超标
    }

    private double calculateHealthScore(Map<String, Double> avgValues, long exceedDays, int totalRecords) {
        double baseScore = 100.0;
        double penalty = (double) exceedDays / totalRecords * 100.0;
        return Math.max(0.0, baseScore - penalty);
    }

    private boolean checkCreditTriggerConditions(Map<String, Object> assessment) {
        double exceedRate = (Double) assessment.get("exceedRate");
        double healthScore = (Double) assessment.get("healthScore");

        // 触发条件：超标率超过30% 且 健康评分低于60分
        return exceedRate > 0.3 && healthScore < 60.0;
    }

    private Map<String, Double> calculateEstimatedGains(NaturalEntity entity, Map<String, Object> assessment) {
        Map<String, Double> gains = new LinkedHashMap<>();

        // 根据实体类别和当前状态估算修复后的生态增量
        String category = entity.getCategory();
        double healthScore = (Double) assessment.get("healthScore");

        // 基础估算逻辑（实际应使用更复杂的AI模型）
        double baseMultiplier = (100.0 - healthScore) / 100.0;

        gains.put("carbonSink", estimateByCategory(category, "carbon", baseMultiplier));
        gains.put("waterPurification", estimateByCategory(category, "water", baseMultiplier));
        gains.put("waterConservation", estimateByCategory(category, "water", baseMultiplier * 0.8));
        gains.put("biodiversity", estimateByCategory(category, "bio", baseMultiplier * 0.6));
        gains.put("climateRegulation", estimateByCategory(category, "climate", baseMultiplier * 0.5));

        return gains;
    }

    private double estimateByCategory(String category, String type, double multiplier) {
        double baseValue = switch (category) {
            case "森林生态" -> type.equals("carbon") ? 50.0 : type.equals("water") ? 30.0 : 20.0;
            case "海洋生态" -> type.equals("carbon") ? 40.0 : type.equals("water") ? 50.0 : 15.0;
            case "河口湿地", "滨海湿地", "淡水湿地" -> type.equals("carbon") ? 30.0 : type.equals("water") ? 60.0 : 25.0;
            case "城市生态" -> type.equals("carbon") ? 10.0 : type.equals("water") ? 20.0 : 15.0;
            default -> 20.0;
        };
        return baseValue * multiplier;
    }

    private double calculateCreditAmount(Map<String, Double> estimatedGains) {
        // 借贷金额 = 预期碳汇增量 * 1.0 + 其他生态增量 * 0.5
        double carbonSink = estimatedGains.getOrDefault("carbonSink", 0.0);
        double otherGains = estimatedGains.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum() - carbonSink;

        return carbonSink * 1.0 + otherGains * 0.5;
    }

    private double calculateEcoTokenLocked(double creditAmount) {
        // 锁定生态币额度 = 借贷金额 * 1.2（120%抵押率）
        return creditAmount * 1.2;
    }

    private double calculateTotalEcoMinted(Map<String, Double> actualGains) {
        // 总铸造生态币 = 实际碳汇 * 1.0 + 水质净化 * 0.8 + 水源涵养 * 0.6 + 生物多样性 * 1.2 + 气候调节 * 0.7
        return actualGains.getOrDefault("carbonSink", 0.0) * 1.0
                + actualGains.getOrDefault("waterPurification", 0.0) * 0.8
                + actualGains.getOrDefault("waterConservation", 0.0) * 0.6
                + actualGains.getOrDefault("biodiversity", 0.0) * 1.2
                + actualGains.getOrDefault("climateRegulation", 0.0) * 0.7;
    }

    private String generateContractAddress() {
        return "0x" + UUID.randomUUID().toString().replace("-", "").substring(0, 40);
    }

    private String buildAiAssessmentReport(Map<String, Object> assessment) {
        StringBuilder report = new StringBuilder();
        report.append("AI生态评估报告\n");
        report.append("================\n");
        report.append("评估时间：").append(assessment.get("assessmentTime")).append("\n");
        report.append("监测周期：").append(assessment.get("monitoringPeriod")).append("\n");
        report.append("监测记录数：").append(assessment.get("totalRecords")).append("\n");
        report.append("超标天数：").append(assessment.get("exceedDays")).append("\n");
        report.append("超标率：").append(String.format("%.2f%%", (Double) assessment.get("exceedRate") * 100)).append("\n");
        report.append("生态健康评分：").append(String.format("%.2f", (Double) assessment.get("healthScore"))).append("\n");
        report.append("平均指标值：").append(assessment.get("avgValues")).append("\n");
        return report.toString();
    }

    private String buildBaselineDataSnapshot(List<VitalSignRecord> records) {
        StringBuilder snapshot = new StringBuilder();
        snapshot.append("基线数据快照\n");
        snapshot.append("============\n");
        snapshot.append("记录数：").append(records.size()).append("\n");
        snapshot.append("时间范围：").append(records.get(0).getRecordedAt())
                .append(" 至 ").append(records.get(records.size() - 1).getRecordedAt()).append("\n");
        snapshot.append("最新记录：\n");
        records.stream().skip(Math.max(0, records.size() - 5)).forEach(r ->
                snapshot.append("  ").append(r.getSensorType()).append(": ")
                        .append(r.getValue()).append(" (").append(r.getRecordedAt()).append(")\n")
        );
        return snapshot.toString();
    }
}