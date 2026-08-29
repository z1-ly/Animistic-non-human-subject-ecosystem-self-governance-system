package com.ecovoice.controller;

import com.ecovoice.domain.*;
import com.ecovoice.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ecological")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EcologicalCreditController {

    private final EcologicalCreditService creditService;
    private final EcologicalFundingService fundingService;
    private final EcologicalRestorationService restorationService;
    private final EcoCoinMintingService mintingService;

    // ========== 生态借贷相关接口 ==========

    @PostMapping("/credit/auto-trigger")
    public Map<String, Object> autoTriggerCredit(@RequestBody Map<String, Object> payload) {
        Long entityId = ((Number) payload.get("entityId")).longValue();
        EcologicalCreditContract contract = creditService.autoTriggerCreditApplication(entityId);
        return Map.of(
                "success", true,
                "contract", contract,
                "message", "生态借贷申请已自动触发");
    }

    @PostMapping("/credit/{contractId}/approve")
    public Map<String, Object> approveCredit(@PathVariable Long contractId) {
        EcologicalCreditContract contract = creditService.approveCreditContract(contractId);
        return Map.of(
                "success", true,
                "contract", contract,
                "message", "生态借贷合约已批准");
    }

    @GetMapping("/credit/pending")
    public List<EcologicalCreditContract> getPendingContracts() {
        return creditService.getPendingContracts();
    }

    @GetMapping("/credit/verification-needed")
    public List<EcologicalCreditContract> getContractsNeedingVerification() {
        return creditService.getContractsNeedingVerification();
    }

    // ========== 生态募资相关接口 ==========

    @PostMapping("/funding/invest")
    public Map<String, Object> invest(@RequestBody Map<String, Object> payload) {
        Long contractId = ((Number) payload.get("contractId")).longValue();
        Long humanNodeId = ((Number) payload.get("humanNodeId")).longValue();
        Double investmentAmount = ((Number) payload.get("investmentAmount")).doubleValue();

        EnterpriseInvestment investment = fundingService.investInRestorationProject(contractId, humanNodeId, investmentAmount);
        return Map.of(
                "success", true,
                "investment", investment,
                "message", "投资认购成功");
    }

    @GetMapping("/funding/available")
    public List<Map<String, Object>> getAvailableProjects() {
        return fundingService.getAvailableProjects();
    }

    @GetMapping("/funding/contract/{contractId}")
    public List<EnterpriseInvestment> getInvestmentsByContract(@PathVariable Long contractId) {
        return fundingService.getInvestmentsByContract(contractId);
    }

    @GetMapping("/funding/human/{humanNodeId}")
    public List<EnterpriseInvestment> getInvestmentsByHumanNode(@PathVariable Long humanNodeId) {
        return fundingService.getInvestmentsByHumanNode(humanNodeId);
    }

    @GetMapping("/funding/expected-return/{investmentId}")
    public Map<String, Object> getExpectedReturn(@PathVariable Long investmentId) {
        return fundingService.calculateExpectedReturn(investmentId);
    }

    @GetMapping("/funding/statistics")
    public Map<String, Object> getFundingStatistics() {
        return fundingService.getFundingStatistics();
    }

    // ========== 生态修复工程相关接口 ==========

    @PostMapping("/restoration/create-project")
    public Map<String, Object> createRestorationProject(@RequestBody Map<String, Object> payload) {
        Long entityId = ((Number) payload.get("entityId")).longValue();
        Double repairBudget = ((Number) payload.get("repairBudget")).doubleValue();

        EcologicalCreditContract contract = restorationService.createRestorationProject(entityId, repairBudget);
        return Map.of(
                "success", true,
                "project", contract,
                "message", "修复工程创建成功");
    }

    @GetMapping("/restoration/projects")
    public List<Map<String, Object>> getAllProjects() {
        List<EcologicalCreditContract> contracts = restorationService.getAllProjects();
        return contracts.stream().map(c -> {
            List<RestorationTask> tasks = restorationService.getTasksByContract(c.getId());
            Map<String, Object> progress = restorationService.getProjectProgress(c.getId());
            return Map.of(
                    "id", c.getId(),
                    "entityName", c.getEntityName(),
                    "entityLocation", "",
                    "repairBudget", c.getRepairBudget(),
                    "tasks", tasks,
                    "progress", progress
            );
        }).collect(java.util.stream.Collectors.toList());
    }

    @GetMapping("/restoration/project/{projectId}/tasks")
    public List<RestorationTask> getTasksByProject(@PathVariable Long projectId) {
        return restorationService.getTasksByContract(projectId);
    }

    @GetMapping("/restoration/project/{projectId}/progress")
    public Map<String, Object> getProjectProgressById(@PathVariable Long projectId) {
        return restorationService.getProjectProgress(projectId);
    }

    @PostMapping("/restoration/create-tasks/{contractId}")
    public Map<String, Object> createRestorationTasks(@PathVariable Long contractId) {
        List<RestorationTask> tasks = restorationService.createRestorationTasks(contractId);
        return Map.of(
                "success", true,
                "tasks", tasks,
                "message", "修复工程任务已创建");
    }

    @PostMapping("/restoration/task/{taskId}/start")
    public Map<String, Object> startTask(@PathVariable Long taskId, @RequestBody Map<String, Object> payload) {
        Long contractorNodeId = ((Number) payload.get("contractorNodeId")).longValue();
        String contractorName = (String) payload.get("contractorName");

        RestorationTask task = restorationService.startTask(taskId, contractorNodeId, contractorName);
        return Map.of(
                "success", true,
                "task", task,
                "message", "修复任务已开始");
    }

    @PostMapping("/restoration/task/{taskId}/submit")
    public Map<String, Object> submitTaskCompletion(@PathVariable Long taskId, @RequestBody Map<String, Object> payload) {
        Double completionPercentage = ((Number) payload.get("completionPercentage")).doubleValue();
        String sitePhotos = (String) payload.get("sitePhotos");
        String beforeAfterComparison = (String) payload.get("beforeAfterComparison");
        String sensorDataSnapshot = (String) payload.get("sensorDataSnapshot");

        RestorationTask task = restorationService.submitTaskCompletion(
                taskId, completionPercentage, sitePhotos, beforeAfterComparison, sensorDataSnapshot);
        return Map.of(
                "success", true,
                "task", task,
                "message", "修复任务完成报告已提交");
    }

    @PostMapping("/restoration/task/{taskId}/verify")
    public Map<String, Object> verifyTask(@PathVariable Long taskId) {
        RestorationTask task = restorationService.verifyTaskCompletion(taskId);
        return Map.of(
                "success", true,
                "task", task,
                "message", "修复任务核验完成");
    }

    @GetMapping("/restoration/contract/{contractId}/tasks")
    public List<RestorationTask> getTasksByContract(@PathVariable Long contractId) {
        return restorationService.getTasksByContract(contractId);
    }

    @GetMapping("/restoration/contract/{contractId}/progress")
    public Map<String, Object> getProjectProgress(@PathVariable Long contractId) {
        return restorationService.getProjectProgress(contractId);
    }

    // ========== 生态币铸造和清算相关接口 ==========

    @PostMapping("/minting/{contractId}")
    public Map<String, Object> mintEcoTokens(@PathVariable Long contractId, @RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Double> actualGains = (Map<String, Double>) payload.get("actualGains");

        List<EcoTokenMinting> mintingRecords = mintingService.mintEcoTokens(contractId, actualGains);
        return Map.of(
                "success", true,
                "mintingRecords", mintingRecords,
                "message", "多维生态币铸造完成");
    }

    @PostMapping("/settlement/{contractId}")
    public Map<String, Object> settleInvestments(@PathVariable Long contractId) {
        List<EcoTokenSettlement> settlements = mintingService.settleInvestments(contractId);
        return Map.of(
                "success", true,
                "settlements", settlements,
                "message", "投资清算完成");
    }

    @PostMapping("/cross-entity/transaction")
    public Map<String, Object> createCrossEntityTransaction(@RequestBody Map<String, Object> payload) {
        Long fromEntityId = ((Number) payload.get("fromEntityId")).longValue();
        Long toEntityId = ((Number) payload.get("toEntityId")).longValue();
        String serviceTypeStr = (String) payload.get("serviceType");
        Double serviceQuantity = ((Number) payload.get("serviceQuantity")).doubleValue();
        String serviceDescription = (String) payload.get("serviceDescription");
        Boolean isRecurring = payload.get("isRecurring") != null ? (Boolean) payload.get("isRecurring") : false;
        Integer recurrenceIntervalDays = payload.get("recurrenceIntervalDays") != null ?
                ((Number) payload.get("recurrenceIntervalDays")).intValue() : null;

        EcoServiceType serviceType = EcoServiceType.valueOf(serviceTypeStr);

        CrossEntityTransaction transaction = mintingService.createCrossEntityTransaction(
                fromEntityId, toEntityId, serviceType, serviceQuantity,
                serviceDescription, isRecurring, recurrenceIntervalDays);

        return Map.of(
                "success", true,
                "transaction", transaction,
                "message", "跨主体生态服务交易完成");
    }

    @PostMapping("/cross-entity/process-recurring")
    public Map<String, Object> processRecurringTransactions() {
        List<CrossEntityTransaction> processedTransactions = mintingService.processRecurringTransactions();
        return Map.of(
                "success", true,
                "processedCount", processedTransactions.size(),
                "transactions", processedTransactions,
                "message", "周期性交易处理完成");
    }

    @GetMapping("/cross-entity/statistics/{entityId}")
    public Map<String, Object> getCrossEntityStatistics(
            @PathVariable Long entityId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDateTime start = LocalDateTime.parse(startDate);
        LocalDateTime end = LocalDateTime.parse(endDate);

        return mintingService.getCrossEntityStatistics(entityId, start, end);
    }
}