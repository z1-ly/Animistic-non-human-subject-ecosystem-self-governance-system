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
 * 生态募资服务
 * 简化版：企业从智能合约借出生态币认购生态修复项目
 * 智能合约作为唯一的生态币资金池，恒定总量
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EcologicalFundingService {

    private final EnterpriseInvestmentRepository investmentRepository;
    private final EcologicalCreditContractRepository contractRepository;
    private final EntityIdentityRepository identityRepository;
    private final HumanNodeRepository humanNodeRepository;
    private final SmartContractRepository smartContractRepository;
    private final EcoCoinService ecoCoinService;
    private final BlockchainLedgerService ledgerService;

    /**
     * 企业投资认购生态修复项目
     * 企业从智能合约借出生态币认购修复项目
     */
    @Transactional
    public EnterpriseInvestment investInRestorationProject(
            Long contractId, Long humanNodeId, Double investmentAmount) {

        EcologicalCreditContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

        if (contract.getContractStatus() != CreditContractStatus.APPROVED) {
            throw new IllegalStateException("合约状态不允许投资");
        }

        HumanNode humanNode = humanNodeRepository.findById(humanNodeId)
                .orElseThrow(() -> new IllegalArgumentException("人类节点不存在"));

        // 获取智能合约
        SmartContract smartContract = smartContractRepository.findByContractName("主生态币资金池")
                .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));

        // 检查智能合约可用余额
        if (smartContract.getAvailableBalance() < investmentAmount) {
            throw new IllegalStateException("智能合约可用余额不足");
        }

        // 计算投资比例
        double currentTotalFunding = investmentRepository.getTotalFundingByContractId(contractId);
        double newTotalFunding = currentTotalFunding + investmentAmount;
        double investmentRatio = investmentAmount / contract.getRepairBudget();

        if (newTotalFunding > contract.getRepairBudget()) {
            throw new IllegalStateException("投资金额超过项目总预算");
        }

        // 从智能合约借出生态币给企业
        smartContract.lend(investmentAmount);
        smartContractRepository.save(smartContract);

        // 将生态币转入企业钱包
        humanNode.setWalletBalance(humanNode.getWalletBalance() + investmentAmount);
        humanNode.setTotalEarned(humanNode.getTotalEarned() + investmentAmount);
        humanNodeRepository.save(humanNode);

        // 创建投资记录
        EnterpriseInvestment investment = EnterpriseInvestment.builder()
                .contractId(contractId)
                .humanNodeId(humanNodeId)
                .enterpriseName(humanNode.getDisplayName())
                .investmentAmount(investmentAmount)
                .ecoTokenLocked(investmentAmount)  // 借贷金额
                .investmentRatio(investmentRatio)
                .investmentStatus(InvestmentStatus.CONFIRMED)
                .investedAt(LocalDateTime.now())
                .build();

        investment = investmentRepository.save(investment);

        // 记录区块链交易
        ledgerService.appendTransaction(
                LedgerTransactionType.BOUNTY_ESCROW,
                contract.getEntityId(), investment.getId(),
                smartContract.getContractAddress(),
                humanNode.getWalletAddress(),
                investmentAmount,
                "企业投资认购 · " + humanNode.getDisplayName()
                        + " · 从智能合约借出：" + investmentAmount + " 生态币"
                        + " · 投资比例：" + String.format("%.2f%%", investmentRatio * 100));

        log.info("企业投资认购成功：企业={}, 合约={}, 借贷金额={}, 投资比例={}",
                humanNode.getDisplayName(), contract.getContractAddress(), investmentAmount, investmentRatio);

        // 检查是否达到募资目标
        checkFundingCompletion(contractId);

        return investment;
    }

    /**
     * 检查募资是否完成
     */
    @Transactional
    private void checkFundingCompletion(Long contractId) {
        EcologicalCreditContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

        double totalFunding = investmentRepository.getTotalFundingByContractId(contractId);

        if (totalFunding >= contract.getRepairBudget() * 0.95) {  // 达到95%即算完成
            // 更新投资状态为已锁定
            List<EnterpriseInvestment> investments = investmentRepository
                    .findLockedInvestmentsByContractId(contractId);

            investments.forEach(inv -> {
                inv.setInvestmentStatus(InvestmentStatus.LOCKED);
                investmentRepository.save(inv);
            });

            // 更新合约状态为已募资
            contract.setContractStatus(CreditContractStatus.FUNDED);
            contractRepository.save(contract);

            log.info("生态修复项目募资完成：合约地址={}, 总募资金额={}",
                    contract.getContractAddress(), totalFunding);
        }
    }

    /**
     * 获取项目的所有投资记录
     */
    @Transactional(readOnly = true)
    public List<EnterpriseInvestment> getInvestmentsByContract(Long contractId) {
        return investmentRepository.findByContractId(contractId);
    }

    /**
     * 获取企业的所有投资记录
     */
    @Transactional(readOnly = true)
    public List<EnterpriseInvestment> getInvestmentsByHumanNode(Long humanNodeId) {
        return investmentRepository.findByHumanNodeId(humanNodeId);
    }

    /**
     * 获取可投资的项目列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAvailableProjects() {
        List<EcologicalCreditContract> contracts = contractRepository
                .findByContractStatus(CreditContractStatus.APPROVED);

        return contracts.stream().map(contract -> {
            double totalFunding = investmentRepository.getTotalFundingByContractId(contract.getId());
            double remainingBudget = contract.getRepairBudget() - totalFunding;
            double fundingProgress = totalFunding / contract.getRepairBudget() * 100;

            Map<String, Object> project = new LinkedHashMap<>();
            project.put("contractId", contract.getId());
            project.put("contractAddress", contract.getContractAddress());
            project.put("entityName", contract.getEntityName());
            project.put("repairBudget", contract.getRepairBudget());
            project.put("totalFunding", totalFunding);
            project.put("remainingBudget", remainingBudget);
            project.put("fundingProgress", String.format("%.2f%%", fundingProgress));
            project.put("estimatedCarbonSink", contract.getEstimatedCarbonSink());
            project.put("estimatedWaterPurification", contract.getEstimatedWaterPurification());
            project.put("estimatedBiodiversity", contract.getEstimatedBiodiversity());
            project.put("createdAt", contract.getCreatedAt());
            return project;
        }).collect(Collectors.toList());
    }

    /**
     * 计算企业预期回报
     */
    @Transactional(readOnly = true)
    public Map<String, Object> calculateExpectedReturn(Long investmentId) {
        EnterpriseInvestment investment = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new IllegalArgumentException("投资记录不存在"));

        EcologicalCreditContract contract = contractRepository.findById(investment.getContractId())
                .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

        double ratio = investment.getInvestmentRatio();

        // 预期回报 = 投资比例 * 预期生态增量 * 对应生态币价格
        double expectedCarbonSink = contract.getEstimatedCarbonSink() * ratio;
        double expectedWaterPurification = contract.getEstimatedWaterPurification() * ratio;
        double expectedWaterConservation = contract.getEstimatedWaterConservation() * ratio;
        double expectedBiodiversity = contract.getEstimatedBiodiversity() * ratio;
        double expectedClimateRegulation = contract.getEstimatedClimateRegulation() * ratio;

        // 计算预期生态币回报（使用不同品类的生态币价格）
        double expectedEcoReturn = expectedCarbonSink * 1.0
                + expectedWaterPurification * 0.8
                + expectedWaterConservation * 0.6
                + expectedBiodiversity * 1.2
                + expectedClimateRegulation * 0.7;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("investmentId", investmentId);
        result.put("enterpriseName", investment.getEnterpriseName());
        result.put("investmentAmount", investment.getInvestmentAmount());
        result.put("investmentRatio", String.format("%.2f%%", ratio * 100));
        result.put("expectedCarbonSink", expectedCarbonSink);
        result.put("expectedWaterPurification", expectedWaterPurification);
        result.put("expectedWaterConservation", expectedWaterConservation);
        result.put("expectedBiodiversity", expectedBiodiversity);
        result.put("expectedClimateRegulation", expectedClimateRegulation);
        result.put("expectedEcoReturn", expectedEcoReturn);
        result.put("expectedReturnRate", String.format("%.2f%%", (expectedEcoReturn / investment.getInvestmentAmount()) * 100));

        return result;
    }

    /**
     * 获取募资统计信息
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFundingStatistics() {
        List<EcologicalCreditContract> allContracts = contractRepository.findAll();
        List<EnterpriseInvestment> allInvestments = investmentRepository.findAll();

        long totalProjects = allContracts.size();
        long fundedProjects = allContracts.stream()
                .filter(c -> c.getContractStatus() == CreditContractStatus.FUNDED
                        || c.getContractStatus() == CreditContractStatus.IN_REPAIR
                        || c.getContractStatus() == CreditContractStatus.IN_MONITORING
                        || c.getContractStatus() == CreditContractStatus.COMPLETED)
                .count();

        double totalInvestment = allInvestments.stream()
                .mapToDouble(EnterpriseInvestment::getInvestmentAmount)
                .sum();

        double totalEcoLocked = allInvestments.stream()
                .mapToDouble(EnterpriseInvestment::getEcoTokenLocked)
                .sum();

        Map<Long, List<EnterpriseInvestment>> investmentsByContract = allInvestments.stream()
                .collect(Collectors.groupingBy(EnterpriseInvestment::getContractId));

        double avgInvestorsPerProject = investmentsByContract.values().stream()
                .mapToInt(List::size)
                .average()
                .orElse(0.0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProjects", totalProjects);
        stats.put("fundedProjects", fundedProjects);
        stats.put("fundingRate", String.format("%.2f%%", (double) fundedProjects / totalProjects * 100));
        stats.put("totalInvestment", totalInvestment);
        stats.put("totalEcoLocked", totalEcoLocked);
        stats.put("totalInvestors", allInvestments.size());
        stats.put("avgInvestorsPerProject", String.format("%.2f", avgInvestorsPerProject));
        stats.put("avgInvestmentPerProject", String.format("%.2f", totalInvestment / fundedProjects));

        return stats;
    }
}