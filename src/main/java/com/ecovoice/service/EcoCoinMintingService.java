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
 * 生态币分配服务
 * 简化版：从智能合约资金池分配生态币，不铸造新币
 * 智能合约作为唯一的生态币资金池，恒定总量
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EcoCoinMintingService {

    private final EcoTokenMintingRepository mintingRepository;
    private final EcoTokenSettlementRepository settlementRepository;
    private final CrossEntityTransactionRepository crossTransactionRepository;
    private final EcologicalCreditContractRepository contractRepository;
    private final EnterpriseInvestmentRepository investmentRepository;
    private final EntityIdentityRepository identityRepository;
    private final NaturalEntityRepository entityRepository;
    private final HumanNodeRepository humanNodeRepository;
    private final SmartContractRepository smartContractRepository;
    private final EcoCoinService ecoCoinService;
    private final BlockchainLedgerService ledgerService;

    // 生态币价格配置（每单位生态价值对应的生态币数量）
    private static final Map<EcoTokenType, Double> ECO_PRICE_PER_UNIT = Map.of(
            EcoTokenType.CARBON_SINK, 1.0,              // 碳汇：1吨CO₂ = 1生态币
            EcoTokenType.WATER_PURIFICATION, 0.8,      // 水质净化：1吨污染物当量 = 0.8生态币
            EcoTokenType.WATER_CONSERVATION, 0.6,      // 水源涵养：1立方米 = 0.6生态币
            EcoTokenType.BIODIVERSITY, 1.2,            // 生物多样性：1信用单位 = 1.2生态币
            EcoTokenType.CLIMATE_REGULATION, 0.7,      // 气候调节：1调节单位 = 0.7生态币
            EcoTokenType.SOIL_CONSERVATION, 0.8,       // 水土保持：1吨泥沙 = 0.8生态币
            EcoTokenType.AIR_PURIFICATION, 0.7,        // 空气净化：1吨污染物 = 0.7生态币
            EcoTokenType.OXYGEN_RELEASE, 0.5,          // 释氧：1吨O₂ = 0.5生态币
            EcoTokenType.POLLINATION, 0.6,             // 授粉：1hectare·年 = 0.6生态币
            EcoTokenType.FLOOD_BUFFER, 0.9             // 洪水调蓄：1立方米 = 0.9生态币
    );

    /**
     * AI核算实际生态增量并分配生态币（从智能合约资金池分配）
     */
    @Transactional
    public List<EcoTokenMinting> mintEcoTokens(Long contractId, Map<String, Double> actualGains) {
        EcologicalCreditContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

        if (contract.getContractStatus() != CreditContractStatus.IN_MONITORING) {
            throw new IllegalStateException("合约状态不允许分配生态币");
        }

        // 获取智能合约
        SmartContract smartContract = smartContractRepository.findByContractName("主生态币资金池")
                .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));

        List<EcoTokenMinting> mintingRecords = new ArrayList<>();
        LocalDateTime monitoringEnd = LocalDateTime.now();
        LocalDateTime monitoringStart = monitoringEnd.minusDays(contract.getMonitoringDays());

        // 计算各类生态增量的生态币价值
        double totalEcoToAllocate = 0.0;
        
        totalEcoToAllocate += calculateEcoValue(EcoTokenType.CARBON_SINK, actualGains.getOrDefault("carbonSink", 0.0));
        totalEcoToAllocate += calculateEcoValue(EcoTokenType.WATER_PURIFICATION, actualGains.getOrDefault("waterPurification", 0.0));
        totalEcoToAllocate += calculateEcoValue(EcoTokenType.WATER_CONSERVATION, actualGains.getOrDefault("waterConservation", 0.0));
        totalEcoToAllocate += calculateEcoValue(EcoTokenType.BIODIVERSITY, actualGains.getOrDefault("biodiversity", 0.0));
        totalEcoToAllocate += calculateEcoValue(EcoTokenType.CLIMATE_REGULATION, actualGains.getOrDefault("climateRegulation", 0.0));

        // 检查智能合约可用余额
        if (smartContract.getAvailableBalance() < totalEcoToAllocate) {
            throw new IllegalStateException("智能合约可用余额不足，无法分配生态币");
        }

        // 从智能合约分配生态币
        smartContract.allocate(totalEcoToAllocate);
        smartContractRepository.save(smartContract);

        // 将生态币转入自然体钱包
        EntityIdentity identity = identityRepository.findById(contract.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("主体钱包未初始化"));
        identity.setWalletBalance(identity.getWalletBalance() + totalEcoToAllocate);
        identity.setTotalEarned(identity.getTotalEarned() + totalEcoToAllocate);
        identityRepository.save(identity);

        // 创建分配记录
        mintingRecords.add(createMintingRecord(contract, EcoTokenType.CARBON_SINK, 
            actualGains.getOrDefault("carbonSink", 0.0), monitoringStart, monitoringEnd));
        mintingRecords.add(createMintingRecord(contract, EcoTokenType.WATER_PURIFICATION, 
            actualGains.getOrDefault("waterPurification", 0.0), monitoringStart, monitoringEnd));
        mintingRecords.add(createMintingRecord(contract, EcoTokenType.WATER_CONSERVATION, 
            actualGains.getOrDefault("waterConservation", 0.0), monitoringStart, monitoringEnd));
        mintingRecords.add(createMintingRecord(contract, EcoTokenType.BIODIVERSITY, 
            actualGains.getOrDefault("biodiversity", 0.0), monitoringStart, monitoringEnd));
        mintingRecords.add(createMintingRecord(contract, EcoTokenType.CLIMATE_REGULATION, 
            actualGains.getOrDefault("climateRegulation", 0.0), monitoringStart, monitoringEnd));

        mintingRecords = mintingRepository.saveAll(mintingRecords);

        // 更新合约
        contract.setTotalEcoMinted(totalEcoToAllocate);
        contract.setActualCarbonSink(actualGains.getOrDefault("carbonSink", 0.0));
        contract.setActualWaterPurification(actualGains.getOrDefault("waterPurification", 0.0));
        contract.setActualWaterConservation(actualGains.getOrDefault("waterConservation", 0.0));
        contract.setActualBiodiversity(actualGains.getOrDefault("biodiversity", 0.0));
        contract.setActualClimateRegulation(actualGains.getOrDefault("climateRegulation", 0.0));
        contractRepository.save(contract);

        // 记录区块链交易
        ledgerService.appendTransaction(
                LedgerTransactionType.ECO_REGENERATION,
                contract.getEntityId(), contract.getId(),
                smartContract.getContractAddress(),
                identity.getWalletAddress(),
                totalEcoToAllocate,
                "生态币分配 · " + contract.getEntityName() + " · 分配金额：" + totalEcoToAllocate + " 生态币");

        log.info("生态币分配完成：合约地址={}, 分配金额={}", contract.getContractAddress(), totalEcoToAllocate);

        return mintingRecords;
    }

    private double calculateEcoValue(EcoTokenType tokenType, double quantity) {
        double pricePerUnit = ECO_PRICE_PER_UNIT.getOrDefault(tokenType, 1.0);
        return quantity * pricePerUnit;
    }

    private EcoTokenMinting createMintingRecord(EcologicalCreditContract contract, EcoTokenType tokenType, 
            double quantity, LocalDateTime startTime, LocalDateTime endTime) {
        double ecoValue = calculateEcoValue(tokenType, quantity);
        return EcoTokenMinting.builder()
                .contractId(contract.getId())
                .entityId(contract.getEntityId())
                .entityName(contract.getEntityName())
                .tokenType(tokenType)
                .actualValue(quantity)
                .unit(tokenType.getUnit())
                .ecoPricePerUnit(ECO_PRICE_PER_UNIT.getOrDefault(tokenType, 1.0))
                .ecoAmountMinted(ecoValue)
                .monitoringPeriodStart(startTime)
                .monitoringPeriodEnd(endTime)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 精准清算：按企业投资比例分配生态币
     * 从自然体钱包分配生态币给企业
     */
    @Transactional
    public List<EcoTokenSettlement> settleInvestments(Long contractId) {
        EcologicalCreditContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("借贷合约不存在"));

        EntityIdentity identity = identityRepository.findById(contract.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("主体钱包未初始化"));

        List<EnterpriseInvestment> investments = investmentRepository.findByContractId(contractId);
        List<EcoTokenMinting> mintingRecords = mintingRepository.findByContractId(contractId);

        if (investments.isEmpty()) {
            throw new IllegalStateException("没有投资记录");
        }

        List<EcoTokenSettlement> settlements = new ArrayList<>();

        // 为每个投资企业创建清算记录
        for (EnterpriseInvestment investment : investments) {
            double ratio = investment.getInvestmentRatio();

            // 按投资比例分配各类生态币
            double carbonSinkAllocated = allocateByRatio(mintingRecords, EcoTokenType.CARBON_SINK, ratio);
            double waterPurificationAllocated = allocateByRatio(mintingRecords, EcoTokenType.WATER_PURIFICATION, ratio);
            double waterConservationAllocated = allocateByRatio(mintingRecords, EcoTokenType.WATER_CONSERVATION, ratio);
            double biodiversityAllocated = allocateByRatio(mintingRecords, EcoTokenType.BIODIVERSITY, ratio);
            double climateRegulationAllocated = allocateByRatio(mintingRecords, EcoTokenType.CLIMATE_REGULATION, ratio);

            // 计算总分配生态币
            double totalEcoAllocated = carbonSinkAllocated + waterPurificationAllocated
                    + waterConservationAllocated + biodiversityAllocated + climateRegulationAllocated;

            // 计算预期回报
            double expectedReturn = investment.getInvestmentAmount() * 1.5;  // 预期50%回报

            // 计算实际回报率
            double returnRate = totalEcoAllocated / investment.getInvestmentAmount();
            double shortfallAmount = Math.max(0.0, expectedReturn - totalEcoAllocated);

            // 创建清算记录
            EcoTokenSettlement settlement = EcoTokenSettlement.builder()
                    .contractId(contractId)
                    .investmentId(investment.getId())
                    .humanNodeId(investment.getHumanNodeId())
                    .enterpriseName(investment.getEnterpriseName())
                    .investmentRatio(ratio)
                    .investmentAmount(investment.getInvestmentAmount())
                    .ecoTokenLocked(investment.getEcoTokenLocked())
                    .totalEcoAllocated(totalEcoAllocated)
                    .carbonSinkAllocated(carbonSinkAllocated)
                    .waterPurificationAllocated(waterPurificationAllocated)
                    .waterConservationAllocated(waterConservationAllocated)
                    .biodiversityAllocated(biodiversityAllocated)
                    .climateRegulationAllocated(climateRegulationAllocated)
                    .expectedReturn(expectedReturn)
                    .actualReturn(totalEcoAllocated)
                    .returnRate(returnRate)
                    .shortfallAmount(shortfallAmount)
                    .settlementStatus(SettlementStatus.COMPLETED)
                    .settledAt(LocalDateTime.now())
                    .settlementReport(buildSettlementReport(investment, totalEcoAllocated, shortfallAmount))
                    .build();

            settlement = settlementRepository.save(settlement);

            // 从自然体钱包向企业分配生态币
            HumanNode humanNode = humanNodeRepository.findById(investment.getHumanNodeId())
                    .orElseThrow(() -> new IllegalArgumentException("人类节点不存在"));

            identity.setWalletBalance(identity.getWalletBalance() - totalEcoAllocated);
            identity.setTotalSpent(identity.getTotalSpent() + totalEcoAllocated);
            identityRepository.save(identity);

            humanNode.setWalletBalance(humanNode.getWalletBalance() + totalEcoAllocated);
            humanNode.setTotalEarned(humanNode.getTotalEarned() + totalEcoAllocated);
            humanNodeRepository.save(humanNode);

            // 记录区块链交易
            ledgerService.appendTransaction(
                    LedgerTransactionType.BOUNTY_PAYOUT,
                    contract.getEntityId(), settlement.getId(),
                    identity.getWalletAddress(),
                    humanNode.getWalletAddress(),
                    totalEcoAllocated,
                    "投资清算 · " + humanNode.getDisplayName()
                            + " · 分配生态币：" + totalEcoAllocated + " · 回报率：" + String.format("%.2f%%", returnRate * 100));

            settlements.add(settlement);

            log.info("投资清算完成：企业={}, 分配生态币={}, 回报率={}%",
                    investment.getEnterpriseName(), totalEcoAllocated, returnRate * 100);
        }

        // 更新合约状态为已完成
        contract.setContractStatus(CreditContractStatus.COMPLETED);
        contract.setCompletedAt(LocalDateTime.now());
        contract.setRepaymentStatus("COMPLETED");
        contractRepository.save(contract);

        return settlements;
    }

    /**
     * 跨主体生态服务交易
     * 自然主体之间进行生态服务结算
     */
    @Transactional
    public CrossEntityTransaction createCrossEntityTransaction(
            Long fromEntityId, Long toEntityId,
            EcoServiceType serviceType, Double serviceQuantity,
            String serviceDescription, Boolean isRecurring, Integer recurrenceIntervalDays) {

        EntityIdentity from = identityRepository.findById(fromEntityId)
                .orElseThrow(() -> new IllegalArgumentException("转出主体不存在"));
        EntityIdentity to = identityRepository.findById(toEntityId)
                .orElseThrow(() -> new IllegalArgumentException("转入主体不存在"));

        if (fromEntityId.equals(toEntityId)) {
            throw new IllegalArgumentException("不能与自己交易");
        }

        // 计算生态币金额
        double ecoAmount = calculateServicePrice(serviceType, serviceQuantity);

        // 检查转出主体余额
        double available = from.getWalletBalance() - from.getLockedBalance();
        if (available < ecoAmount) {
            throw new IllegalStateException("转出主体生态币余额不足");
        }

        // 执行转账
        from.setWalletBalance(from.getWalletBalance() - ecoAmount);
        from.setTotalSpent(from.getTotalSpent() + ecoAmount);
        identityRepository.save(from);

        to.setWalletBalance(to.getWalletBalance() + ecoAmount);
        to.setTotalEarned(to.getTotalEarned() + ecoAmount);
        identityRepository.save(to);

        // 记录交易
        String transactionHash = generateTransactionHash();
        LocalDateTime nextPaymentDate = null;
        if (isRecurring && recurrenceIntervalDays != null && recurrenceIntervalDays > 0) {
            nextPaymentDate = LocalDateTime.now().plusDays(recurrenceIntervalDays);
        }

        CrossEntityTransaction transaction = CrossEntityTransaction.builder()
                .fromEntityId(fromEntityId)
                .fromEntityName(entityName(fromEntityId))
                .toEntityId(toEntityId)
                .toEntityName(entityName(toEntityId))
                .serviceType(serviceType)
                .serviceDescription(serviceDescription)
                .serviceQuantity(serviceQuantity)
                .serviceUnit(getServiceUnit(serviceType))
                .ecoAmount(ecoAmount)
                .transactionHash(transactionHash)
                .isRecurring(isRecurring)
                .recurrenceIntervalDays(recurrenceIntervalDays)
                .nextPaymentDate(nextPaymentDate)
                .transactedAt(LocalDateTime.now())
                .build();

        transaction = crossTransactionRepository.save(transaction);

        // 记录区块链交易
        ledgerService.appendTransaction(
                LedgerTransactionType.ENTITY_TRANSFER,
                fromEntityId, transaction.getId(),
                from.getWalletAddress(),
                to.getWalletAddress(),
                ecoAmount,
                "跨主体生态服务交易 · " + serviceType.getLabel()
                        + " · " + entityName(fromEntityId) + " → " + entityName(toEntityId)
                        + " · 金额：" + ecoAmount + " 生态币");

        log.info("跨主体生态服务交易完成：服务类型={}, 金额={}, {} → {}",
                serviceType.getLabel(), ecoAmount, entityName(fromEntityId), entityName(toEntityId));

        return transaction;
    }

    /**
     * 处理周期性跨主体交易
     */
    @Transactional
    public List<CrossEntityTransaction> processRecurringTransactions() {
        LocalDateTime now = LocalDateTime.now();
        List<CrossEntityTransaction> recurringTransactions = crossTransactionRepository
                .findByIsRecurringTrueAndNextPaymentDateBefore(now);

        List<CrossEntityTransaction> processedTransactions = new ArrayList<>();

        for (CrossEntityTransaction recurringTx : recurringTransactions) {
            try {
                // 创建新的交易记录
                CrossEntityTransaction newTx = createCrossEntityTransaction(
                        recurringTx.getFromEntityId(),
                        recurringTx.getToEntityId(),
                        recurringTx.getServiceType(),
                        recurringTx.getServiceQuantity(),
                        recurringTx.getServiceDescription(),
                        true,
                        recurringTx.getRecurrenceIntervalDays()
                );

                // 更新原交易的下次支付日期
                recurringTx.setNextPaymentDate(LocalDateTime.now().plusDays(recurringTx.getRecurrenceIntervalDays()));
                crossTransactionRepository.save(recurringTx);

                processedTransactions.add(newTx);

                log.info("周期性交易已处理：交易ID={}", recurringTx.getId());
            } catch (Exception e) {
                log.error("处理周期性交易失败：交易ID={}, 错误={}", recurringTx.getId(), e.getMessage());
            }
        }

        return processedTransactions;
    }

    /**
     * 获取跨主体交易统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCrossEntityStatistics(Long entityId, LocalDateTime startDate, LocalDateTime endDate) {
        double totalReceived = crossTransactionRepository.getTotalReceivedByEntityIdAndPeriod(entityId, startDate, endDate);
        double totalPaid = crossTransactionRepository.getTotalPaidByEntityIdAndPeriod(entityId, startDate, endDate);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("entityId", entityId);
        stats.put("entityName", entityName(entityId));
        stats.put("period", startDate + " 至 " + endDate);
        stats.put("totalReceived", totalReceived);
        stats.put("totalPaid", totalPaid);
        stats.put("netFlow", totalReceived - totalPaid);

        return stats;
    }

    // ========== 私有辅助方法 ==========

    private double allocateByRatio(List<EcoTokenMinting> mintingRecords, EcoTokenType tokenType, double ratio) {
        return mintingRecords.stream()
                .filter(m -> m.getTokenType() == tokenType)
                .mapToDouble(m -> Math.round(m.getEcoAmountMinted() * ratio * 100.0) / 100.0)
                .findFirst()
                .orElse(0.0);
    }

    private double calculateServicePrice(EcoServiceType serviceType, Double quantity) {
        // 根据服务类型计算价格
        double basePrice = switch (serviceType) {
            case WATER_PURIFICATION_SERVICE -> 0.8;
            case WATER_SUPPLY_SERVICE -> 0.6;
            case FLOOD_REGULATION_SERVICE -> 0.9;
            case WIND_BREAK_SERVICE -> 0.7;
            case POLLINATION_SERVICE -> 0.6;
            case SOIL_CONSERVATION_SERVICE -> 0.8;
            case AIR_PURIFICATION_SERVICE -> 0.7;
            case CARBON_SEQUESTRATION_SERVICE -> 1.0;
            case BIODIVERSITY_SUPPORT -> 1.2;
            case CLIMATE_REGULATION_SERVICE -> 0.7;
        };

        return Math.round(quantity * basePrice * 100.0) / 100.0;
    }

    private String getServiceUnit(EcoServiceType serviceType) {
        return switch (serviceType) {
            case WATER_PURIFICATION_SERVICE -> "吨污染物当量";
            case WATER_SUPPLY_SERVICE -> "立方米";
            case FLOOD_REGULATION_SERVICE -> "立方米调蓄容量";
            case WIND_BREAK_SERVICE -> "防护面积";
            case POLLINATION_SERVICE -> "授粉服务hectare·年";
            case SOIL_CONSERVATION_SERVICE -> "吨泥沙拦截量";
            case AIR_PURIFICATION_SERVICE -> "吨污染物当量";
            case CARBON_SEQUESTRATION_SERVICE -> "吨CO₂等价";
            case BIODIVERSITY_SUPPORT -> "生物多样性信用单位";
            case CLIMATE_REGULATION_SERVICE -> "调节服务单位";
        };
    }

    private String generateTransactionHash() {
        return "0x" + UUID.randomUUID().toString().replace("-", "").substring(0, 64);
    }

    private String buildAiCalculationReport(EcoTokenType tokenType, double actualValue, double ecoPricePerUnit, double ecoAmountMinted) {
        StringBuilder report = new StringBuilder();
        report.append("AI生态增量核算报告\n");
        report.append("==================\n");
        report.append("生态币类型：").append(tokenType.getLabel()).append("\n");
        report.append("实际生态增量：").append(actualValue).append(" ").append(tokenType.getUnit()).append("\n");
        report.append("生态币单价：").append(ecoPricePerUnit).append(" 生态币/").append(tokenType.getUnit()).append("\n");
        report.append("铸造生态币数量：").append(ecoAmountMinted).append(" 生态币\n");
        report.append("核算时间：").append(LocalDateTime.now()).append("\n");
        return report.toString();
    }

    private String buildSettlementReport(EnterpriseInvestment investment, double totalEcoAllocated, double shortfallAmount) {
        StringBuilder report = new StringBuilder();
        report.append("生态币清算报告\n");
        report.append("==============\n");
        report.append("企业名称：").append(investment.getEnterpriseName()).append("\n");
        report.append("投资金额：").append(investment.getInvestmentAmount()).append(" 生态币\n");
        report.append("投资比例：").append(String.format("%.2f%%", investment.getInvestmentRatio() * 100)).append("\n");
        report.append("分配生态币：").append(totalEcoAllocated).append(" 生态币\n");
        report.append("回报率：").append(String.format("%.2f%%", (totalEcoAllocated / investment.getInvestmentAmount()) * 100)).append("\n");
        if (shortfallAmount > 0) {
            report.append("缺口金额：").append(shortfallAmount).append(" 生态币（由企业自行承担）\n");
        }
        report.append("清算时间：").append(LocalDateTime.now()).append("\n");
        return report.toString();
    }

    private String entityName(Long entityId) {
        return identityRepository.findById(entityId)
                .map(id -> {
                    NaturalEntity entity = entityRepository.findById(id.getEntityId()).orElse(null);
                    return entity != null ? entity.getName() : "未知主体";
                })
                .orElse("未知主体");
    }
}