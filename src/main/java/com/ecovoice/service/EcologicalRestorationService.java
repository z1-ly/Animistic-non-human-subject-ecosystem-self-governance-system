package com.ecovoice.service;

import com.ecovoice.domain.*;
import com.ecovoice.repository.RestorationTaskRepository;
import com.ecovoice.repository.EcologicalCreditContractRepository;
import com.ecovoice.repository.HumanNodeRepository;
import com.ecovoice.repository.NaturalEntityRepository;
import com.ecovoice.repository.NatureAppealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生态修复工程服务
 * 实现工程款智能托管、按工序权重自动拨付、AI进度核验
 * 参考：UW-003案例中的工程款拆分和AI核验机制
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EcologicalRestorationService {

    private final RestorationTaskRepository taskRepository;
    private final EcologicalCreditContractRepository contractRepository;
    private final HumanNodeRepository humanNodeRepository;
    private final NaturalEntityRepository naturalEntityRepository;
    private final NatureAppealRepository appealRepository;
    private final EcoCoinService ecoCoinService;
    private final BlockchainLedgerService ledgerService;
    private final EcoCoinLoanService ecoCoinLoanService;
    private final VitalRecordService vitalRecordService;

    // 默认工程权重配置
    private static final Map<RestorationTaskType, Double> DEFAULT_TASK_WEIGHTS = Map.of(
            RestorationTaskType.ALGAE_REMOVAL, 0.30,           // 除藻净水 30%
            RestorationTaskType.VEGETATION_RESTORATION, 0.24, // 水生植被重构 24%
            RestorationTaskType.OXYGENATION, 0.30,            // 生态增氧 30%
            RestorationTaskType.BIOLOGICAL_RESTORATION, 0.16  // 生物群落修复 16%
    );

    /**
     * 从悬赏任务创建修复工程
     * 当人类节点接取任务时自动创建，用于后续评估和结算
     */
    @Transactional
    public EcologicalCreditContract createProjectFromBounty(Long appealId, Long humanNodeId) {
        NatureAppeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("诉求不存在"));
        
        NaturalEntity entity = naturalEntityRepository.findById(appeal.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("环境实体不存在"));

        HumanNode humanNode = humanNodeRepository.findById(humanNodeId)
                .orElseThrow(() -> new IllegalArgumentException("人类节点不存在"));

        double repairBudget = appeal.getBountyAmount() != null ? appeal.getBountyAmount() : 0.0;

        String uuidWithoutHyphens = UUID.randomUUID().toString().replace("-", "");
        String contractAddress = "0x" + String.format("%-40s", uuidWithoutHyphens).replace(' ', '0');

        EcologicalCreditContract contract = EcologicalCreditContract.builder()
                .contractAddress(contractAddress)
                .entityId(entity.getId())
                .entityName(entity.getName())
                .creditAmount(repairBudget)
                .ecoTokenLocked(repairBudget)
                .repairBudget(repairBudget)
                .repairDays(30)
                .monitoringDays(30)
                .totalDays(60)
                .contractStatus(CreditContractStatus.FUNDED)
                .applicationReason("悬赏任务接取 · " + appeal.getAppealType().getLabel())
                .repaymentDeadline(LocalDateTime.now().plusDays(60))
                .estimatedCarbonSink(0.0)
                .estimatedWaterPurification(0.0)
                .estimatedWaterConservation(0.0)
                .estimatedBiodiversity(0.0)
                .estimatedClimateRegulation(0.0)
                .build();

        contract = contractRepository.save(contract);

        RestorationTaskType taskType = mapAppealTypeToTaskType(appeal.getAppealType());
        if (taskType == null) {
            taskType = RestorationTaskType.BIOLOGICAL_RESTORATION;
        }

        RestorationTask task = RestorationTask.builder()
                .contractId(contract.getId())
                .taskType(taskType)
                .taskName(appeal.getAppealType().getLabel())
                .taskDescription(appeal.getMessage())
                .budgetAmount(repairBudget)
                .releasedAmount(0.0)
                .taskWeight(1.0)
                .taskStatus(TaskStatus.IN_PROGRESS)
                .completionPercentage(0.0)
                .contractorName(humanNode.getDisplayName())
                .contractorWallet(humanNode.getWalletAddress())
                .startedAt(LocalDateTime.now())
                .build();

        taskRepository.save(task);

        ledgerService.appendTransaction(
                LedgerTransactionType.CONTRACT_DEPLOY,
                entity.getId(), contract.getId(),
                "ENTITY_" + entity.getId(),
                contractAddress, 0,
                "悬赏修复工程部署 · " + entity.getName() + " · " + appeal.getAppealType().getLabel() + " · 预算: " + repairBudget + " ECO"
        );

        log.info("从悬赏任务创建修复工程：诉求ID={}, 实体={}, 施工方={}, 预算={}",
                appealId, entity.getName(), humanNode.getDisplayName(), repairBudget);

        return contract;
    }

    private RestorationTaskType mapAppealTypeToTaskType(AppealType appealType) {
        return switch (appealType) {
            case WATER_QUALITY -> RestorationTaskType.ALGAE_REMOVAL;
            case THIRST -> RestorationTaskType.OXYGENATION;
            case HEAT_STRESS, COLD_STRESS -> RestorationTaskType.VEGETATION_RESTORATION;
            case NOISE_DAYTIME, QUIET_REQUEST, DRY_AIR -> RestorationTaskType.BIOLOGICAL_RESTORATION;
            default -> RestorationTaskType.BIOLOGICAL_RESTORATION;
        };
    }

    /**
     * 创建独立的生态修复工程（不依赖借贷合约流程）
     * 自动从智能合约借币并创建修复任务
     */
    @Transactional
    public EcologicalCreditContract createRestorationProject(Long entityId, double repairBudget) {
        NaturalEntity entity = naturalEntityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("环境实体不存在"));

        // 从智能合约借币
        ecoCoinLoanService.borrowCoins(
                entityId,
                repairBudget,
                "生态修复工程 · " + entity.getName() + " · 修复预算: " + repairBudget + " ECO"
        );

        // 生成合约地址
        String contractAddress = "0x" + UUID.randomUUID().toString().replace("-", "").substring(0, 40);

        // 创建修复工程合约
        EcologicalCreditContract contract = EcologicalCreditContract.builder()
                .contractAddress(contractAddress)
                .entityId(entityId)
                .entityName(entity.getName())
                .creditAmount(repairBudget)
                .ecoTokenLocked(repairBudget)
                .estimatedCarbonSink(0.0)
                .estimatedWaterPurification(0.0)
                .estimatedWaterConservation(0.0)
                .estimatedBiodiversity(0.0)
                .estimatedClimateRegulation(0.0)
                .repairBudget(repairBudget)
                .repairDays(90)
                .monitoringDays(90)
                .totalDays(180)
                .contractStatus(CreditContractStatus.FUNDED)
                .applicationReason("创建生态修复工程")
                .repaymentDeadline(LocalDateTime.now().plusDays(180))
                .build();

        contract = contractRepository.save(contract);

        // 创建修复任务
        List<RestorationTask> tasks = new ArrayList<>();
        for (Map.Entry<RestorationTaskType, Double> entry : DEFAULT_TASK_WEIGHTS.entrySet()) {
            RestorationTaskType taskType = entry.getKey();
            double weight = entry.getValue();
            double budgetAmount = repairBudget * weight;

            RestorationTask task = RestorationTask.builder()
                    .contractId(contract.getId())
                    .taskType(taskType)
                    .taskName(taskType.getLabel())
                    .taskDescription(generateTaskDescription(taskType))
                    .budgetAmount(budgetAmount)
                    .releasedAmount(0.0)
                    .taskWeight(weight)
                    .taskStatus(TaskStatus.PENDING)
                    .completionPercentage(0.0)
                    .build();

            tasks.add(taskRepository.save(task));
        }

        // 记录账本交易
        ledgerService.appendTransaction(
                LedgerTransactionType.CONTRACT_DEPLOY,
                entityId, contract.getId(),
                "ENTITY_" + entityId,
                contractAddress, 0,
                "生态修复工程部署 · " + entity.getName() + " · 预算: " + repairBudget + " ECO"
        );

        log.info("生态修复工程已创建：实体={}, 合约地址={}, 预算={}, 任务数={}",
                entity.getName(), contractAddress, repairBudget, tasks.size());

        return contract;
    }

    /**
     * 获取所有修复工程列表
     */
    @Transactional(readOnly = true)
    public List<EcologicalCreditContract> getAllProjects() {
        return contractRepository.findAll().stream()
                .filter(c -> c.getContractStatus() != CreditContractStatus.COMPLETED)
                .collect(Collectors.toList());
    }

    /**
     * 为借贷合约创建修复工程任务
     * 按工序权重自动拆分工程款
     */
    @Transactional
    public List<RestorationTask> createRestorationTasks(Long contractId) {
        EcologicalCreditContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

        if (contract.getContractStatus() != CreditContractStatus.FUNDED) {
            throw new IllegalStateException("合约状态不允许创建修复任务");
        }

        List<RestorationTask> tasks = new ArrayList<>();
        double totalBudget = contract.getRepairBudget();

        // 按权重创建四大板块任务
        for (Map.Entry<RestorationTaskType, Double> entry : DEFAULT_TASK_WEIGHTS.entrySet()) {
            RestorationTaskType taskType = entry.getKey();
            double weight = entry.getValue();
            double budgetAmount = totalBudget * weight;

            RestorationTask task = RestorationTask.builder()
                    .contractId(contractId)
                    .taskType(taskType)
                    .taskName(taskType.getLabel())
                    .taskDescription(generateTaskDescription(taskType))
                    .budgetAmount(budgetAmount)
                    .releasedAmount(0.0)
                    .taskWeight(weight)
                    .taskStatus(TaskStatus.PENDING)
                    .completionPercentage(0.0)
                    .build();

            tasks.add(taskRepository.save(task));
        }

        log.info("生态修复工程任务已创建：合约地址={}, 任务数={}",
                contract.getContractAddress(), tasks.size());

        return tasks;
    }

    /**
     * 施工方开始执行任务
     */
    @Transactional
    public RestorationTask startTask(Long taskId, Long contractorNodeId, String contractorName) {
        RestorationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("修复任务不存在"));

        if (task.getTaskStatus() != TaskStatus.PENDING) {
            throw new IllegalStateException("任务状态不允许开始");
        }

        HumanNode contractor = humanNodeRepository.findById(contractorNodeId)
                .orElseThrow(() -> new IllegalArgumentException("施工方不存在"));

        task.setTaskStatus(TaskStatus.IN_PROGRESS);
        task.setContractorName(contractorName);
        task.setContractorWallet(contractor.getWalletAddress());
        task.setStartedAt(LocalDateTime.now());
        task = taskRepository.save(task);

        log.info("修复任务已开始：任务ID={}, 施工方={}", taskId, contractorName);
        return task;
    }

    /**
     * 施工方提交完成报告
     */
    @Transactional
    public RestorationTask submitTaskCompletion(
            Long taskId, Double completionPercentage,
            String sitePhotos, String beforeAfterComparison,
            String sensorDataSnapshot) {

        RestorationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("修复任务不存在"));

        if (task.getTaskStatus() != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("任务状态不允许提交完成");
        }

        task.setTaskStatus(TaskStatus.SUBMITTED);
        task.setCompletionPercentage(completionPercentage);
        task.setSitePhotos(sitePhotos);
        task.setBeforeAfterComparison(beforeAfterComparison);
        task.setSensorDataSnapshot(sensorDataSnapshot);
        task = taskRepository.save(task);

        log.info("修复任务完成报告已提交：任务ID={}, 完成度={}%", taskId, completionPercentage);
        return task;
    }

    /**
     * AI核验任务完成情况
     * 结合实时传感数据交叉核验，达标后自动释放对应工程款
     */
    @Transactional
    public RestorationTask verifyTaskCompletion(Long taskId) {
        RestorationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("修复任务不存在"));

        if (task.getTaskStatus() != TaskStatus.SUBMITTED) {
            throw new IllegalStateException("任务状态不允许核验");
        }

        // AI核验：结合传感数据和施工记录
        Map<String, Object> verificationResult = performAiVerification(task);

        double verificationScore = (Double) verificationResult.get("verificationScore");
        boolean isApproved = (Boolean) verificationResult.get("isApproved");

        if (isApproved) {
            // 核验通过，释放工程款
            double releaseAmount = calculateReleaseAmount(task, verificationScore);
            releaseTaskPayment(task, releaseAmount);

            task.setTaskStatus(TaskStatus.VERIFIED);
            task.setAiVerificationScore(verificationScore);
            task.setVerificationReport(buildVerificationReport(verificationResult));
            task.setVerifiedAt(LocalDateTime.now());

            // 如果完成度达到100%，标记为已完成
            if (task.getCompletionPercentage() >= 100.0) {
                task.setTaskStatus(TaskStatus.COMPLETED);
                task.setCompletedAt(LocalDateTime.now());
            }

            log.info("修复任务核验通过：任务ID={}, 核验分数={}, 释放金额={}",
                    taskId, verificationScore, releaseAmount);
        } else {
            // 核验未通过
            task.setTaskStatus(TaskStatus.REJECTED);
            task.setAiVerificationScore(verificationScore);
            task.setVerificationReport(buildVerificationReport(verificationResult));

            log.info("修复任务核验未通过：任务ID={}, 核验分数={}", taskId, verificationScore);
        }

        return taskRepository.save(task);
    }

    /**
     * 检查合约所有任务是否完成
     */
    @Transactional
    public boolean checkAllTasksCompleted(Long contractId) {
        List<RestorationTask> tasks = taskRepository.findByContractId(contractId);

        boolean allCompleted = tasks.stream()
                .allMatch(t -> t.getTaskStatus() == TaskStatus.COMPLETED);

        if (allCompleted) {
            // 更新合约状态为监测验证中
            EcologicalCreditContract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

            contract.setContractStatus(CreditContractStatus.IN_MONITORING);
            contractRepository.save(contract);

            // 任务完成，归还生态币给智能合约
            try {
                if (contract.getEntityId() != null) {
                    // 计算需要归还的金额（任务预算总额）
                    double totalBudget = tasks.stream()
                            .mapToDouble(RestorationTask::getBudgetAmount)
                            .sum();

                    String repayMessage = ecoCoinLoanService.repayCoins(
                            contract.getEntityId(),
                            totalBudget,
                            "生态修复工程任务全部完成，归还借入的生态币"
                    );

                    log.info("生态修复工程全部完成，已归还生态币：合约地址={}, 归还金额={}, 原因={}",
                            contract.getContractAddress(), totalBudget, repayMessage);
                }
            } catch (Exception e) {
                log.error("归还生态币失败：合约地址={}, 错误={}", contract.getContractAddress(), e.getMessage());
            }

            log.info("生态修复工程全部完成，进入监测验证阶段：合约地址={}",
                    contract.getContractAddress());
        }

        return allCompleted;
    }

    /**
     * 获取合约的所有修复任务
     */
    @Transactional(readOnly = true)
    public List<RestorationTask> getTasksByContract(Long contractId) {
        return taskRepository.findByContractId(contractId);
    }

    /**
     * 获取工程进度统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getProjectProgress(Long contractId) {
        List<RestorationTask> tasks = taskRepository.findByContractId(contractId);

        double totalBudget = tasks.stream().mapToDouble(RestorationTask::getBudgetAmount).sum();
        double totalReleased = tasks.stream().mapToDouble(RestorationTask::getReleasedAmount).sum();
        double avgCompletion = tasks.stream().mapToDouble(RestorationTask::getCompletionPercentage).average().orElse(0.0);

        long completedTasks = tasks.stream().filter(t -> t.getTaskStatus() == TaskStatus.COMPLETED).count();
        long inProgressTasks = tasks.stream().filter(t -> t.getTaskStatus() == TaskStatus.IN_PROGRESS).count();
        long pendingTasks = tasks.stream().filter(t -> t.getTaskStatus() == TaskStatus.PENDING).count();

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("totalTasks", tasks.size());
        progress.put("completedTasks", completedTasks);
        progress.put("inProgressTasks", inProgressTasks);
        progress.put("pendingTasks", pendingTasks);
        progress.put("totalBudget", totalBudget);
        progress.put("totalReleased", totalReleased);
        progress.put("remainingBudget", totalBudget - totalReleased);
        progress.put("releaseProgress", String.format("%.2f%%", (totalReleased / totalBudget) * 100));
        progress.put("avgCompletion", String.format("%.2f%%", avgCompletion));

        return progress;
    }

    // ========== 私有辅助方法 ==========

    private String generateTaskDescription(RestorationTaskType taskType) {
        return switch (taskType) {
            case ALGAE_REMOVAL -> "全域机械除藻与漂浮污染物清理，解决水体黑臭、藻类泛滥问题";
            case VEGETATION_RESTORATION -> "沉水植物种植，重构水下植被体系，恢复水体自净能力与固碳载体";
            case OXYGENATION -> "布设太阳能曝气机，持续提升水体溶解氧，重建水生生物生存环境";
            case BIOLOGICAL_RESTORATION -> "投放食藻虫、底栖生物，构建自然生态制衡体系";
            default -> "生态修复工程任务";
        };
    }

    private Map<String, Object> performAiVerification(RestorationTask task) {
        Map<String, Object> result = new LinkedHashMap<>();

        EcologicalCreditContract contract = contractRepository.findById(task.getContractId()).orElse(null);
        Long entityId = contract != null ? contract.getEntityId() : null;

        double environmentImprovementScore = 0.0;
        List<Map<String, Object>> sensorAnalysis = new ArrayList<>();

        if (entityId != null && task.getStartedAt() != null) {
            LocalDateTime baselineTime = task.getStartedAt().minusHours(24);
            LocalDateTime currentTime = LocalDateTime.now();

            for (SensorType sensorType : SensorType.values()) {
                List<VitalSignRecord> baselineRecords = vitalRecordService.findByEntityIdAndSensorTypeAndRecordedAtBetween(entityId, sensorType, baselineTime.minusHours(24), baselineTime);
                List<VitalSignRecord> currentRecords = vitalRecordService.findByEntityIdAndSensorTypeAndRecordedAtBetween(entityId, sensorType, currentTime.minusHours(24), currentTime);

                if (!baselineRecords.isEmpty() && !currentRecords.isEmpty()) {
                    double baselineAvg = baselineRecords.stream().mapToDouble(VitalSignRecord::getValue).average().orElse(0);
                    double currentAvg = currentRecords.stream().mapToDouble(VitalSignRecord::getValue).average().orElse(0);

                    double improvement = calculateSensorImprovement(sensorType, baselineAvg, currentAvg);
                    environmentImprovementScore += improvement;

                    Map<String, Object> sensorResult = new LinkedHashMap<>();
                    sensorResult.put("sensorType", sensorType.name());
                    sensorResult.put("sensorLabel", sensorType.getLabel());
                    sensorResult.put("baselineValue", String.format("%.2f", baselineAvg));
                    sensorResult.put("currentValue", String.format("%.2f", currentAvg));
                    sensorResult.put("normalRange", sensorType.getNormalMin() + " ~ " + sensorType.getNormalMax());
                    sensorResult.put("improvement", String.format("%.2f%%", improvement * 100));
                    sensorAnalysis.add(sensorResult);
                }
            }

            environmentImprovementScore = Math.min(environmentImprovementScore / SensorType.values().length * 100, 80);
        } else {
            environmentImprovementScore = 40;
        }

        double completionFactor = task.getCompletionPercentage() / 100.0;
        double verificationScore = environmentImprovementScore * completionFactor + 20;
        verificationScore = Math.min(100.0, Math.max(0.0, verificationScore));

        boolean isApproved = verificationScore >= 70.0;

        result.put("verificationScore", Math.round(verificationScore * 100.0) / 100.0);
        result.put("isApproved", isApproved);
        result.put("environmentImprovementScore", Math.round(environmentImprovementScore * 100.0) / 100.0);
        result.put("completionFactor", completionFactor);
        result.put("sensorAnalysis", sensorAnalysis);
        result.put("verificationTime", LocalDateTime.now());

        return result;
    }

    private double calculateSensorImprovement(SensorType sensorType, double baseline, double current) {
        double normalMin = sensorType.getNormalMin();
        double normalMax = sensorType.getNormalMax();
        double normalMid = (normalMin + normalMax) / 2;

        double baselineDistance = Math.abs(baseline - normalMid);
        double currentDistance = Math.abs(current - normalMid);

        if (baselineDistance == 0) {
            return currentDistance == 0 ? 1.0 : 0.5;
        }

        double improvement = 1 - (currentDistance / baselineDistance);

        boolean baselineInRange = baseline >= normalMin && baseline <= normalMax;
        boolean currentInRange = current >= normalMin && current <= normalMax;

        if (currentInRange && !baselineInRange) {
            improvement = Math.min(improvement + 0.2, 1.0);
        } else if (!currentInRange && baselineInRange) {
            improvement = Math.max(improvement - 0.3, 0.0);
        }

        return Math.max(0.0, Math.min(1.0, improvement));
    }

    private double calculateReleaseAmount(RestorationTask task, double verificationScore) {
        // 释放金额 = 预算金额 * (完成度/100) * (核验分数/100)
        double releaseRatio = (task.getCompletionPercentage() / 100.0) * (verificationScore / 100.0);
        return Math.round(task.getBudgetAmount() * releaseRatio * 100.0) / 100.0;
    }

    @Transactional
    private void releaseTaskPayment(RestorationTask task, double releaseAmount) {
        if (releaseAmount <= 0) return;

        // 从合约托管账户释放资金到施工方
        // 这里简化处理，实际应该从智能合约托管账户释放
        HumanNode contractor = humanNodeRepository.findByWalletAddress(task.getContractorWallet())
                .orElse(null);

        if (contractor != null) {
            contractor.setWalletBalance(contractor.getWalletBalance() + releaseAmount);
            contractor.setTotalEarned(contractor.getTotalEarned() + releaseAmount);
            humanNodeRepository.save(contractor);

            // 记录区块链交易
            ledgerService.appendTransaction(
                    LedgerTransactionType.BOUNTY_PAYOUT,
                    task.getContractId(), task.getId(),
                    task.getContractorWallet(),
                    contractor.getWalletAddress(),
                    releaseAmount,
                    "工程款释放 · " + task.getTaskName()
                            + " · 核验分数：" + task.getAiVerificationScore()
                            + " · 释放金额：" + releaseAmount + " 生态币");
        }

        // 更新任务释放金额
        task.setReleasedAmount(task.getReleasedAmount() + releaseAmount);
        taskRepository.save(task);
    }

    private String buildVerificationReport(Map<String, Object> verificationResult) {
        StringBuilder report = new StringBuilder();
        report.append("AI核验报告\n");
        report.append("==========\n");
        report.append("核验时间：").append(verificationResult.get("verificationTime")).append("\n");
        report.append("核验分数：").append(String.format("%.2f", (Double) verificationResult.get("verificationScore"))).append("\n");
        report.append("核验结果：").append(verificationResult.get("isApproved")).append("\n");
        report.append("基础分：").append(verificationResult.get("baseScore")).append("\n");
        report.append("完成度奖励：").append(String.format("%.2f", (Double) verificationResult.get("completionBonus"))).append("\n");
        report.append("传感数据奖励：").append(verificationResult.get("sensorDataBonus")).append("\n");
        report.append("照片证据奖励：").append(verificationResult.get("photoBonus")).append("\n");
        return report.toString();
    }
}