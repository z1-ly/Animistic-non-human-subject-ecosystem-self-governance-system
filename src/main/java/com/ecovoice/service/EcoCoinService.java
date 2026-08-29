package com.ecovoice.service;

import com.ecovoice.domain.*;
import com.ecovoice.domain.EcologicalValueType;
import com.ecovoice.dto.EcologicalSpendRequest;
import com.ecovoice.dto.EcosphereDto;
import com.ecovoice.dto.EcoCoinTransferRequest;
import com.ecovoice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 生态币（ECO）总控服务。
 * 简化版：智能合约作为唯一的生态币资金池，恒定总量
 * 
 * 简化后的生态币流通闭环：
 *   1) 智能合约初始化：恒定总量生态币（如1,000,000 ECO）
 *   2) 环境实体借贷：环境实体向智能合约借贷生态币用于修复
 *   3) 企业认购：企业从智能合约借出生态币认购修复项目
 *   4) 修复完成：AI核算生态增量，从智能合约分配生态币给环境实体
 *   5) 投资清算：按投资比例从环境实体钱包分配生态币给企业
 *   6) 消耗回流：企业消耗生态币时回流到智能合约，用于未来借贷
 */
@Service
@RequiredArgsConstructor
public class EcoCoinService {

    /** 生态币（ECO）恒定总量。单位：ECO。 */
    public static final double TOTAL_SUPPLY = 100_000;
    public static final String TOKEN_NAME = "生态币";
    public static final String TOKEN_SYMBOL = "ECO";

    private final EntityIdentityRepository identityRepository;
    private final HumanNodeRepository humanNodeRepository;
    private final NaturalEntityRepository entityRepository;
    private final SmartContractRepository smartContractRepository;
    private final BlockchainLedgerService ledgerService;
    private final EcologicalValueCatalogService valueCatalogService;

    // ========== 发行与初始化 ==========

    /**
     * 当前所有钱包加总之和（不包括智能合约资金池）
     * 用于校验恒量 & 统计
     */
    @Transactional(readOnly = true)
    public double circulatingSupply() {
        double entity = identityRepository.findAll()
                .stream()
                .mapToDouble(e -> e.getWalletBalance() + e.getLockedBalance())
                .sum();
        double human = humanNodeRepository.findAll()
                .stream()
                .mapToDouble(HumanNode::getWalletBalance)
                .sum();
        return entity + human;
    }

    /**
     * 获取智能合约资金池可用余额
     */
    @Transactional(readOnly = true)
    public double getSmartContractAvailableBalance() {
        return smartContractRepository.findByContractName("主生态币资金池")
                .map(SmartContract::getAvailableBalance)
                .orElse(0.0);
    }

    // ========== 人类使用生态币兑换生态资源 ==========

    /**
     * 扣减人类节点钱包，回流到智能合约资金池
     * 简化版：所有消耗的生态币都回流到智能合约，用于未来借贷
     */
    @Transactional
    public Map<String, Object> spendEcologicalCredit(EcologicalSpendRequest req) {
        LedgerTransactionType txType = LedgerTransactionType.ECOLOGICAL_FEE;
        double multiplier = req.getSeverityMultiplier() <= 0 ? 1.0 : req.getSeverityMultiplier();
        double total = req.getBaseAmount() * multiplier;
        if (multiplier >= 2.0) txType = LedgerTransactionType.ECO_DESTRUCTION_FINE;

        HumanNode payer;
        String payerAddress;
        if ("HUMAN_NODE".equalsIgnoreCase(req.getSpenderType())) {
            if (req.getHumanNodeId() == null) throw new IllegalArgumentException("缺少 humanNodeId");
            payer = humanNodeRepository.findById(req.getHumanNodeId())
                    .orElseThrow(() -> new IllegalArgumentException("人类节点不存在"));
            if (payer.getWalletBalance() < total) {
                throw new IllegalStateException("人类节点生态币余额不足：需 " + total + " " + TOKEN_SYMBOL);
            }
            payer.setWalletBalance(payer.getWalletBalance() - total);
            humanNodeRepository.save(payer);
            payerAddress = payer.getWalletAddress();
        } else {
            if (req.getEntityId() == null) throw new IllegalArgumentException("缺少 entityId");
            EntityIdentity id = identityRepository.findById(req.getEntityId())
                    .orElseThrow(() -> new IllegalArgumentException("主体不存在"));
            double avail = id.getWalletBalance() - id.getLockedBalance();
            if (avail < total) throw new IllegalStateException("主体可用生态币不足：需 " + total + " " + TOKEN_SYMBOL);
            id.setWalletBalance(id.getWalletBalance() - total);
            identityRepository.save(id);
            payerAddress = id.getWalletAddress();
        }

        // 回流到智能合约资金池
        SmartContract smartContract = smartContractRepository.findByContractName("主生态币资金池")
                .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));
        smartContract.recover(total);
        smartContractRepository.save(smartContract);

        // 记录区块链交易
        ledgerService.appendTransaction(
                txType,
                req.getEntityId() != null ? req.getEntityId() : 0L,
                null,
                payerAddress,
                smartContract.getContractAddress(),
                total,
                (req.getNote() == null ? "生态资源消耗" : req.getNote())
                        + " · 类别 " + req.getSpendCategory()
                        + " · 回流至智能合约资金池"
                        + " · " + total + " " + TOKEN_SYMBOL);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", TOKEN_SYMBOL);
        resp.put("tokenName", TOKEN_NAME);
        resp.put("baseAmount", req.getBaseAmount());
        resp.put("severityMultiplier", multiplier);
        resp.put("totalCharged", total);
        resp.put("spendCategory", req.getSpendCategory());
        resp.put("refluxTarget", "智能合约资金池");
        resp.put("refluxAmount", total);
        resp.put("settledAt", LocalDateTime.now().toString());
        return resp;
    }

    /** 人类使用生态币向非人自然主体兑换生态价值类资源。 */
    @Transactional
    public Map<String, Object> consumePermission(
            Long humanNodeId, Long targetEntityId,
            String valueTypeKey, double amount, String note) {
        EcologicalValueType valueType = resolveValueTypeKey(valueTypeKey);
        valueCatalogService.assertEntityMatches(valueType, targetEntityId);
        if (amount <= 0) throw new IllegalArgumentException("兑换金额必须为正");

        LedgerTransactionType txType = valueType == EcologicalValueType.DAO_GOVERNANCE
                ? LedgerTransactionType.DAO_VOTE_FEE
                : valueType == EcologicalValueType.CULTURE_RESEARCH
                ? LedgerTransactionType.VISITOR_PERMIT_FEE
                : LedgerTransactionType.ECOLOGICAL_FEE;

        HumanNode payer = humanNodeRepository.findById(humanNodeId)
                .orElseThrow(() -> new IllegalArgumentException("人类节点不存在"));
        EntityIdentity target = identityRepository.findById(targetEntityId)
                .orElseThrow(() -> new IllegalArgumentException("目标自然体不存在"));
        if (payer.getWalletBalance() < amount) {
            throw new IllegalStateException("生态币余额不足：需 " + amount + " " + TOKEN_SYMBOL
                    + "，当前 " + payer.getWalletBalance() + " " + TOKEN_SYMBOL);
        }

        payer.setWalletBalance(payer.getWalletBalance() - amount);
        humanNodeRepository.save(payer);

        target.setWalletBalance(target.getWalletBalance() + amount);
        target.setTotalEarned(target.getTotalEarned() + amount);
        identityRepository.save(target);

        ledgerService.appendTransaction(
                txType, targetEntityId, null,
                payer.getWalletAddress(), target.getWalletAddress(), amount,
                "兑换生态资源 · " + valueType.getMajorCategory()
                        + " · " + valueType.getCoreValueIndicator()
                        + " · " + (note == null ? "" : note)
                        + " · 金额 " + amount + " " + TOKEN_SYMBOL);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("valueTypeKey", valueType.getValueTypeKey());
        resp.put("majorCategory", valueType.getMajorCategory());
        resp.put("coreValueIndicator", valueType.getCoreValueIndicator());
        resp.put("unit", valueType.getUnit());
        resp.put("ecoPricePerUnit", valueType.getEcoPricePerUnit());
        resp.put("refluxLogic", valueType.getRefluxLogic());
        resp.put("amount", amount);
        resp.put("token", TOKEN_SYMBOL);
        resp.put("tokenName", TOKEN_NAME);
        resp.put("grantedTo", payer.getDisplayName());
        resp.put("grantedByEntity", entityName(targetEntityId));
        resp.put("humanBalanceAfter", payer.getWalletBalance());
        return resp;
    }

    private EcologicalValueType resolveValueTypeKey(String key) {
        return EcologicalValueType.fromKey(key)
                .or(() -> EcologicalValueType.fromKey(legacyServiceKeyToValueType(key)))
                .orElseThrow(() -> new IllegalArgumentException("未知生态价值类 " + key));
    }

    private String legacyServiceKeyToValueType(String serviceKey) {
        if (serviceKey == null) return null;
        return switch (serviceKey) {
            case "EMISSION_OFFSET" -> "CARBON_SINK";
            case "WATER_QUOTA" -> "WATER_SOURCE";
            case "WOODLAND_MINING" -> "MATERIAL_QUOTA";
            case "VISITOR_PERMIT" -> "CULTURE_RESEARCH";
            case "DAO_VOTE" -> "DAO_GOVERNANCE";
            default -> serviceKey;
        };
    }

    // ========== 跨自然体转账结算 ==========

    @Transactional
    public Map<String, Object> transferBetweenEntities(EcoCoinTransferRequest req) {
        if (req.getFromEntityId().equals(req.getToEntityId())) {
            throw new IllegalArgumentException("不能转账给自己");
        }
        EntityIdentity from = identityRepository.findById(req.getFromEntityId())
                .orElseThrow(() -> new IllegalArgumentException("转出主体不存在"));
        EntityIdentity to = identityRepository.findById(req.getToEntityId())
                .orElseThrow(() -> new IllegalArgumentException("转入主体不存在"));
        if (req.getAmount() <= 0) throw new IllegalArgumentException("金额必须为正");
        double avail = from.getWalletBalance() - from.getLockedBalance();
        if (avail < req.getAmount()) throw new IllegalStateException("转出主体可用储备不足");

        from.setWalletBalance(from.getWalletBalance() - req.getAmount());
        from.setTotalSpent(from.getTotalSpent() + req.getAmount());
        identityRepository.save(from);
        to.setWalletBalance(to.getWalletBalance() + req.getAmount());
        to.setTotalEarned(to.getTotalEarned() + req.getAmount());
        identityRepository.save(to);

        ledgerService.appendTransaction(
                LedgerTransactionType.ENTITY_TRANSFER,
                req.getFromEntityId(), null,
                from.getWalletAddress(), to.getWalletAddress(),
                req.getAmount(),
                "跨自然体结算 · " + entityName(req.getFromEntityId())
                        + " → " + entityName(req.getToEntityId())
                        + " · " + (req.getReason() == null ? "生态协同服务" : req.getReason())
                        + " · 金额 " + req.getAmount() + " " + TOKEN_SYMBOL);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", TOKEN_SYMBOL);
        resp.put("amount", req.getAmount());
        resp.put("fromEntity", Map.of("entityId", from.getEntityId(), "entityName", entityName(from.getEntityId())));
        resp.put("toEntity", Map.of("entityId", to.getEntityId(), "entityName", entityName(to.getEntityId())));
        resp.put("reason", req.getReason());
        resp.put("settledAt", LocalDateTime.now().toString());
        return resp;
    }

    // ========== 刚性回流机制：企业消耗生态币时强制回流到对应自然主体国库 ==========

    /**
     * 企业消耗多维生态币，资金强制回流到对应自然主体国库
     * 用于抵扣碳排放、排污配额、用水额度等生态占用
     */
    @Transactional
    public Map<String, Object> consumeEcoTokenWithMandatoryReflux(
            Long humanNodeId, Long targetEntityId,
            String tokenType, double amount, String consumptionReason) {
        HumanNode consumer = humanNodeRepository.findById(humanNodeId)
                .orElseThrow(() -> new IllegalArgumentException("人类节点不存在"));
        EntityIdentity targetEntity = identityRepository.findById(targetEntityId)
                .orElseThrow(() -> new IllegalArgumentException("目标自然主体不存在"));

        if (consumer.getWalletBalance() < amount) {
            throw new IllegalStateException("生态币余额不足：需 " + amount + " " + TOKEN_SYMBOL
                    + "，当前 " + consumer.getWalletBalance() + " " + TOKEN_SYMBOL);
        }

        // 扣除人类节点生态币
        consumer.setWalletBalance(consumer.getWalletBalance() - amount);
        consumer.setTotalSpent(consumer.getTotalSpent() + amount);
        humanNodeRepository.save(consumer);

        // 强制回流到自然主体国库
        targetEntity.setWalletBalance(targetEntity.getWalletBalance() + amount);
        targetEntity.setTotalEarned(targetEntity.getTotalEarned() + amount);
        identityRepository.save(targetEntity);

        // 记录交易
        LedgerTransactionType txType = determineTransactionType(tokenType);
        ledgerService.appendTransaction(
                txType,
                targetEntityId, null,
                consumer.getWalletAddress(),
                targetEntity.getWalletAddress(),
                amount,
                "刚性回流 · " + tokenType + " · " + (consumptionReason == null ? "生态资源消耗" : consumptionReason)
                        + " · 企业：" + consumer.getDisplayName()
                        + " → 自然主体：" + entityName(targetEntityId)
                        + " · 金额 " + amount + " " + TOKEN_SYMBOL);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", TOKEN_SYMBOL);
        resp.put("tokenType", tokenType);
        resp.put("amount", amount);
        resp.put("consumer", Map.of(
                "humanNodeId", humanNodeId,
                "displayName", consumer.getDisplayName(),
                "balanceAfter", consumer.getWalletBalance()));
        resp.put("targetEntity", Map.of(
                "entityId", targetEntityId,
                "entityName", entityName(targetEntityId),
                "balanceAfter", targetEntity.getWalletBalance()));
        resp.put("consumptionReason", consumptionReason);
        resp.put("refluxMechanism", "刚性回流：资金全额流入自然主体国库，用于新一轮生态修复");
        resp.put("consumedAt", LocalDateTime.now().toString());
        return resp;
    }

    /**
     * 超额消耗加倍扣除机制
     * 当企业出现超额排污、过度用水、破坏生态等行为时，系统自动加倍扣除生态币
     */
    @Transactional
    public Map<String, Object> penalizeOverConsumption(
            Long humanNodeId, Long targetEntityId,
            String violationType, double baseAmount, String violationDetails) {
        double penaltyMultiplier = 2.0; // 加倍扣除
        double totalPenalty = baseAmount * penaltyMultiplier;

        HumanNode violator = humanNodeRepository.findById(humanNodeId)
                .orElseThrow(() -> new IllegalArgumentException("人类节点不存在"));
        EntityIdentity targetEntity = identityRepository.findById(targetEntityId)
                .orElseThrow(() -> new IllegalArgumentException("目标自然主体不存在"));

        if (violator.getWalletBalance() < totalPenalty) {
            throw new IllegalStateException("生态币余额不足以支付惩罚：需 " + totalPenalty + " " + TOKEN_SYMBOL
                    + "，当前 " + violator.getWalletBalance() + " " + TOKEN_SYMBOL);
        }

        // 扣除惩罚金额
        violator.setWalletBalance(violator.getWalletBalance() - totalPenalty);
        violator.setTotalSpent(violator.getTotalSpent() + totalPenalty);
        humanNodeRepository.save(violator);

        // 全额回流到自然主体国库
        targetEntity.setWalletBalance(targetEntity.getWalletBalance() + totalPenalty);
        targetEntity.setTotalEarned(targetEntity.getTotalEarned() + totalPenalty);
        identityRepository.save(targetEntity);

        ledgerService.appendTransaction(
                LedgerTransactionType.ECO_DESTRUCTION_FINE,
                targetEntityId, null,
                violator.getWalletAddress(),
                targetEntity.getWalletAddress(),
                totalPenalty,
                "加倍惩罚 · " + violationType + " · " + (violationDetails == null ? "生态违规行为" : violationDetails)
                        + " · 违规企业：" + violator.getDisplayName()
                        + " · 基础金额：" + baseAmount + " · 惩罚倍数：" + penaltyMultiplier + "x"
                        + " · 总计：" + totalPenalty + " " + TOKEN_SYMBOL);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", TOKEN_SYMBOL);
        resp.put("violationType", violationType);
        resp.put("baseAmount", baseAmount);
        resp.put("penaltyMultiplier", penaltyMultiplier);
        resp.put("totalPenalty", totalPenalty);
        resp.put("violator", Map.of(
                "humanNodeId", humanNodeId,
                "displayName", violator.getDisplayName(),
                "balanceAfter", violator.getWalletBalance()));
        resp.put("targetEntity", Map.of(
                "entityId", targetEntityId,
                "entityName", entityName(targetEntityId),
                "balanceAfter", targetEntity.getWalletBalance()));
        resp.put("violationDetails", violationDetails);
        resp.put("refluxMechanism", "刚性回流：惩罚资金全额流入自然主体国库");
        resp.put("penalizedAt", LocalDateTime.now().toString());
        return resp;
    }

    private LedgerTransactionType determineTransactionType(String tokenType) {
        if (tokenType == null) return LedgerTransactionType.ECOLOGICAL_FEE;
        return switch (tokenType.toUpperCase()) {
            case "CARBON_SINK" -> LedgerTransactionType.ECOLOGICAL_FEE;
            case "WATER_PURIFICATION" -> LedgerTransactionType.ECOLOGICAL_FEE;
            case "WATER_CONSERVATION" -> LedgerTransactionType.ECOLOGICAL_FEE;
            case "BIODIVERSITY" -> LedgerTransactionType.ECOLOGICAL_FEE;
            case "CLIMATE_REGULATION" -> LedgerTransactionType.ECOLOGICAL_FEE;
            default -> LedgerTransactionType.ECOLOGICAL_FEE;
        };
    }

    // ========== 统计视图 ==========

    @Transactional(readOnly = true)
    public EcosphereDto ecosphere() {
        // 总量：智能合约发行的恒定总量（10000）
        double totalSupply = smartContractRepository.findByContractName("主生态币资金池")
                .map(SmartContract::getTotalSupply)
                .orElse(TOTAL_SUPPLY);
        
        // 已流通 = 各自然体已借出的币（从 EntityIdentity 的 borrowedBalance 累加）
        double lentOut = identityRepository.findAll()
                .stream()
                .mapToDouble(EntityIdentity::getBorrowedBalance)
                .sum();
        
        // 智能合约资金池：可用余额 + 已借出
        double smartContractAvailable = getSmartContractAvailableBalance();
        double smartContractLent = smartContractRepository.findByContractName("主生态币资金池")
                .map(SmartContract::getLentBalance)
                .orElse(0.0);

        Map<String, Double> byCat = new LinkedHashMap<>();
        for (EntityIdentity id : identityRepository.findAll()) {
            String cat = entityRepository.findById(id.getEntityId())
                    .map(NaturalEntity::getCategory).orElse("其他");
            byCat.merge(cat, id.getWalletBalance() + id.getLockedBalance(), Double::sum);
        }

        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("lastSpend", findLatestOfType(LedgerTransactionType.ECOLOGICAL_FEE));
        latest.put("lastEntityTransfer", findLatestOfType(LedgerTransactionType.ENTITY_TRANSFER));

        return EcosphereDto.builder()
                .tokenName(TOKEN_NAME)
                .tokenSymbol(TOKEN_SYMBOL)
                .totalSupply(totalSupply)
                .circulatingSupply(lentOut)
                .entityReserve(smartContractAvailable)
                .humanReserve(smartContractLent)
                .smartContractAvailable(smartContractAvailable)
                .smartContractLent(smartContractLent)
                .entityCount(entityRepository.count())
                .humanNodeCount(humanNodeRepository.count())
                .ecosystemReserve(byCat)
                .latestSettle(latest)
                .build();
    }

    private Map<String, Object> findLatestOfType(LedgerTransactionType type) {
        return repository().findAll().stream()
                .filter(t -> t.getType() == type)
                .max(Comparator.comparing(LedgerTransaction::getCreatedAt))
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("txHash", t.getTxHash());
                    m.put("amount", t.getAmount());
                    m.put("payload", t.getPayload());
                    m.put("createdAt", t.getCreatedAt());
                    return m;
                })
                .orElse(Map.of());
    }

    private com.ecovoice.repository.LedgerTransactionRepository repository() {
        return ledgerService.transactionRepository();
    }

    private String entityName(Long id) {
        return entityRepository.findById(id).map(NaturalEntity::getName).orElse("未知自然体");
    }
}
