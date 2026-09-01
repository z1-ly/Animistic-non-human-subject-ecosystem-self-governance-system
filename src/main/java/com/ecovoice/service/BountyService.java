package com.ecovoice.service;

import com.ecovoice.domain.*;
import com.ecovoice.dto.BountyFulfillRequest;
import com.ecovoice.dto.BountyTaskDto;
import com.ecovoice.dto.NatureAppealDto;
import com.ecovoice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BountyService {

    private final NatureAppealRepository appealRepository;
    private final EntityIdentityRepository identityRepository;
    private final NaturalEntityRepository entityRepository;
    private final HumanNodeRepository humanNodeRepository;
    private final SmartContractRepository smartContractRepository;
    private final BlockchainLedgerService ledgerService;
    private final HumanNodeService humanNodeService;
    private final RestorationTaskRepository restorationTaskRepository;
    private final EcologicalRestorationService restorationService;

    public List<BountyTaskDto> listOpenTasks() {
        List<BountyTaskDto> openTasks = appealRepository.findByBountyStatusOrderByCreatedAtDesc(
                com.ecovoice.domain.BountyStatus.OPEN).stream()
                .map(this::toTaskDto).toList();

        if (openTasks.isEmpty()) {
            SmartContract smartContract = smartContractRepository.findByContractName("主生态币资金池").orElse(null);
            if (smartContract != null && smartContract.getAvailableBalance() > 0) {
                autoGenerateTasks(smartContract.getAvailableBalance());
                openTasks = appealRepository.findByBountyStatusOrderByCreatedAtDesc(
                        com.ecovoice.domain.BountyStatus.OPEN).stream()
                        .map(this::toTaskDto).toList();
            }
        }
        return openTasks;
    }

    public List<BountyTaskDto> listAllActiveTasks() {
        return appealRepository.findByBountyStatusInOrderByCreatedAtDesc(
                List.of(com.ecovoice.domain.BountyStatus.OPEN,
                        com.ecovoice.domain.BountyStatus.CLAIMED)).stream()
                .map(this::toTaskDto).toList();
    }

    public List<BountyTaskDto> listMyTasks(Long humanNodeId) {
        return appealRepository.findByClaimantNodeIdOrderByClaimedAtDesc(humanNodeId).stream()
                .filter(a -> a.getBountyStatus() == com.ecovoice.domain.BountyStatus.CLAIMED
                        || a.getBountyStatus() == com.ecovoice.domain.BountyStatus.FULFILLED)
                .map(this::toTaskDto).toList();
    }

    private BountyTaskDto toTaskDto(NatureAppeal appeal) {
        NaturalEntity entity = entityRepository.findById(appeal.getEntityId()).orElse(null);
        String claimantName = resolveClaimantName(appeal.getClaimantNodeId());
        return BountyTaskDto.from(appeal,
                entity != null ? entity.getName() : "未知主体",
                entity != null ? entity.getCategory() : "",
                claimantName);
    }

    private String resolveClaimantName(Long nodeId) {
        if (nodeId == null) return null;
        return humanNodeRepository.findById(nodeId).map(HumanNode::getDisplayName).orElse(null);
    }

    public List<NatureAppealDto> listAppealsWithClaimant(Long entityId) {
        Map<Long, String> names = humanNodeRepository.findAll().stream()
                .collect(Collectors.toMap(HumanNode::getId, HumanNode::getDisplayName));
        return appealRepository.findByEntityIdAndActiveTrueOrderByCreatedAtDesc(entityId).stream()
                .map(a -> NatureAppealDto.from(a, names.get(a.getClaimantNodeId()))).toList();
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void escrowBounty(NatureAppeal appeal) {
        if (appeal.getBountyAmount() == null || appeal.getBountyAmount() <= 0) return;
        double amount = appeal.getBountyAmount();

        SmartContract smartContract = smartContractRepository.findByContractName("主生态币资金池")
                .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));

        if (smartContract.getAvailableBalance() < amount) {
            throw new IllegalStateException("智能合约可用余额不足，无法借币发布悬赏");
        }

        EntityIdentity identity = identityRepository.findById(appeal.getEntityId())
                .orElseThrow(() -> new IllegalStateException("主体钱包未初始化"));

        String contractAddress = ledgerService.generateContractAddress(appeal.getId());
        appeal.setContractAddress(contractAddress);
        appeal.setBountyStatus(com.ecovoice.domain.BountyStatus.OPEN);

        smartContract.lend(amount);
        smartContractRepository.save(smartContract);

        identity.setBorrowedBalance(identity.getBorrowedBalance() + amount);
        identity.setWalletBalance(identity.getWalletBalance() + amount);
        identity.setLockedBalance(identity.getLockedBalance() + amount);
        identityRepository.save(identity);

        ledgerService.appendTransaction(
                LedgerTransactionType.CONTRACT_DEPLOY,
                appeal.getEntityId(), appeal.getId(),
                identity.getWalletAddress(), contractAddress, 0,
                "智能合约部署 · 诉求#" + appeal.getId() + " " + appeal.getAppealType().getLabel());

        ledgerService.appendTransaction(
                LedgerTransactionType.LOAN,
                appeal.getEntityId(), appeal.getId(),
                smartContract.getContractAddress(),
                identity.getWalletAddress(), amount,
                "借币 · 环境实体向智能合约借币 " + amount + " 生态币(" + BlockchainLedgerService.TOKEN_SYMBOL + ")用于发布悬赏 · " + appeal.getMessage());

        ledgerService.appendTransaction(
                LedgerTransactionType.BOUNTY_ESCROW,
                appeal.getEntityId(), appeal.getId(),
                identity.getWalletAddress(), contractAddress, amount,
                "悬赏托管 " + amount + " 生态币(" + BlockchainLedgerService.TOKEN_SYMBOL + ") · " + appeal.getMessage());

        appealRepository.save(appeal);
    }

    @Transactional
    public NatureAppealDto claimBounty(Long appealId, Long humanNodeId) {
        NatureAppeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("诉求不存在"));
        if (appeal.getBountyStatus() != com.ecovoice.domain.BountyStatus.OPEN) {
            throw new IllegalStateException("该任务已被接取或不可接取");
        }
        HumanNode human = humanNodeService.getNodeEntity(humanNodeId);
        NaturalEntity entity = entityRepository.findById(appeal.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("自然体不存在"));

        appeal.setBountyStatus(com.ecovoice.domain.BountyStatus.CLAIMED);
        appeal.setClaimantNodeId(human.getId());
        appeal.setClaimedAt(java.time.LocalDateTime.now());
        appealRepository.save(appeal);
        humanNodeService.recordClaim(human);

        restorationService.createProjectFromBounty(appealId, humanNodeId);

        ledgerService.appendTransaction(
                LedgerTransactionType.BOUNTY_CLAIM,
                appeal.getEntityId(), appealId,
                human.getWalletAddress(), appeal.getContractAddress(), 0,
                human.getDisplayName() + " 接取「" + entity.getName() + "」任务："
                        + appeal.getAppealType().getLabel()
                        + " · 悬赏 " + appeal.getBountyAmount() + " 生态币");

        return NatureAppealDto.from(appeal, human.getDisplayName());
    }

    @Transactional
    public NatureAppealDto fulfillBounty(Long appealId, BountyFulfillRequest request) {
        NatureAppeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("诉求不存在"));
        if (appeal.getBountyStatus() != com.ecovoice.domain.BountyStatus.CLAIMED) {
            throw new IllegalStateException("请先接取任务再提交完成");
        }
        if (!request.getHumanNodeId().equals(appeal.getClaimantNodeId())) {
            throw new IllegalStateException("仅任务接取者可提交完成");
        }

        HumanNode humanNode = humanNodeRepository.findById(appeal.getClaimantNodeId())
                .orElseThrow(() -> new IllegalArgumentException("人类节点不存在"));

        restorationTaskRepository.findByContractorWallet(humanNode.getWalletAddress()).stream()
                .filter(t -> t.getTaskStatus() == TaskStatus.IN_PROGRESS)
                .findFirst()
                .ifPresent(task -> {
                    task.setTaskStatus(TaskStatus.SUBMITTED);
                    task.setCompletionPercentage(100.0);
                    task.setSitePhotos("");
                    task.setBeforeAfterComparison(request.getProofNote());
                    task.setSensorDataSnapshot("");
                    restorationTaskRepository.save(task);
                });

        appeal.setBountyStatus(com.ecovoice.domain.BountyStatus.FULFILLED);
        appeal.setFulfillerName(humanNode.getDisplayName());
        appeal.setFulfilledAt(java.time.LocalDateTime.now());
        appeal.setActive(false);
        appealRepository.save(appeal);

        ledgerService.appendTransaction(
                LedgerTransactionType.BOUNTY_FULFILL,
                appeal.getEntityId(), appeal.getId(),
                humanNode.getWalletAddress(),
                appeal.getContractAddress(), 0,
                "「" + appeal.getAppealType().getLabel() + "」任务已提交评估 · "
                        + (request.getProofNote() == null ? "" : request.getProofNote()));

        return NatureAppealDto.from(appeal, appeal.getFulfillerName());
    }

    @Transactional
    public void autoGenerateTasks(double availableBalance) {
        List<NaturalEntity> entities = entityRepository.findAll();
        if (entities.isEmpty()) {
            return;
        }

        java.util.Random random = new java.util.Random();

        for (NaturalEntity entity : entities) {
            if (availableBalance <= 0) break;

            String category = entity.getCategory();
            AppealType[] types = getAppealTypesForCategory(category);
            
            for (AppealType type : types) {
                if (appealRepository.findFirstByEntityIdAndAppealTypeAndActiveTrue(entity.getId(), type).isPresent()) {
                    continue;
                }

                AppealContent content = generateAppealContent(entity, type, random);
                if (content == null) continue;

                double multiplier = "行星权利主体".equals(category) ? 10.0 : 1.0;
                if ("海洋生态".equals(category) || "滨海湿地".equals(category)) {
                    multiplier = 1.5;
                }
                double bounty = Math.round(content.bountyBase * multiplier * ("HIGH".equals(content.severity) ? 1.8 : 1.0));

                if (bounty > availableBalance) continue;

                NatureAppeal appeal = NatureAppeal.builder()
                        .entityId(entity.getId())
                        .appealType(type)
                        .message(content.message)
                        .actionDetail(content.actionDetail)
                        .actionParameters(content.actionParameters)
                        .measurableTarget(content.measurableTarget)
                        .acceptanceCriteria(content.acceptanceCriteria)
                        .baselineData(content.baselineData)
                        .rewardRules(content.rewardRules)
                        .triggerRule(content.triggerRule)
                        .severity(content.severity)
                        .deadline(LocalDateTime.now().plusHours(content.deadlineHours))
                        .bountyAmount(bounty)
                        .bountyStatus(com.ecovoice.domain.BountyStatus.OPEN)
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .build();

                appealRepository.save(appeal);
                escrowBounty(appeal);
                availableBalance -= bounty;
            }
        }
    }

    private AppealType[] getAppealTypesForCategory(String category) {
        return switch (category) {
            case "荒漠生态" -> new AppealType[]{AppealType.THIRST, AppealType.DRY_AIR, AppealType.HEAT_STRESS};
            case "海洋生态", "滨海湿地" -> new AppealType[]{AppealType.WATER_QUALITY, AppealType.QUIET_REQUEST};
            case "草原生态" -> new AppealType[]{AppealType.THIRST, AppealType.COLD_STRESS, AppealType.HEAT_STRESS};
            case "城市生态" -> new AppealType[]{AppealType.HEAT_STRESS, AppealType.DRY_AIR, AppealType.QUIET_REQUEST};
            case "森林生态" -> new AppealType[]{AppealType.HEAT_STRESS, AppealType.THIRST, AppealType.QUIET_REQUEST};
            case "寒带生态" -> new AppealType[]{AppealType.COLD_STRESS};
            case "河口湿地", "淡水湿地" -> new AppealType[]{AppealType.WATER_QUALITY, AppealType.THIRST};
            default -> new AppealType[]{AppealType.THIRST, AppealType.QUIET_REQUEST};
        };
    }

    private AppealContent generateAppealContent(NaturalEntity entity, AppealType type, java.util.Random random) {
        String category = entity.getCategory();
        String entityName = entity.getName();
        
        return switch (type) {
            case THIRST -> generateThirstAppeal(category, entityName, random);
            case QUIET_REQUEST -> generateQuietRequestAppeal(category, entityName, random);
            case WATER_QUALITY -> generateWaterQualityAppeal(category, entityName, random);
            case HEAT_STRESS -> generateHeatStressAppeal(category, entityName, random);
            case COLD_STRESS -> generateColdStressAppeal(category, entityName, random);
            case DRY_AIR -> generateDryAirAppeal(category, entityName, random);
            case NOISE_DAYTIME -> generateNoiseDaytimeAppeal(category, entityName, random);
        };
    }

    private AppealContent generateThirstAppeal(String category, String entityName, java.util.Random random) {
        double deficit = 10 + random.nextDouble() * 20;
        int waterMl = (int) Math.max(1500, Math.round(deficit * 80));
        String severity = deficit > 15 ? "HIGH" : "MEDIUM";
        int deadlineHours = "HIGH".equals(severity) ? 48 : 72;
        double bountyBase = 400;

        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules, triggerRule;

        if ("荒漠生态".equals(category)) {
            message = entityName + "土壤持续缺水胁迫：连续3天土壤湿度低于30%，胡杨根系吸水困难，幼苗萎蔫率上升，需要精准补水灌溉。";
            actionDetail = "滴灌补水：沿胡杨林带每5米设置1个滴头，实施精准滴灌；地表覆盖：补水后铺设3cm厚秸秆覆盖物减少蒸发；坎儿井水调配：启用备用坎儿井，增加日供水量。";
            actionParameters = "单次补水量不少于" + waterMl + "ml/㎡，分2-3次进行(清晨6:00-8:00为主)；秸秆覆盖厚度≥3cm，覆盖面积不少于灌溉区域90%；滴灌持续时长不少于4小时/次。";
            acceptanceCriteria = "测点：均匀布设5个土壤湿度监测点(0-20cm土层)，土壤湿度传感器精度±2%RH；阈值：24h土壤湿度均值恢复至30%-70%RH；外观核验：秸秆覆盖完整无裸露，滴灌设施运行正常，拍照留档。";
            baselineData = "连续3天土壤湿度日均值低于30%，今日缺水差值" + String.format("%.1f", deficit) + "%";
            rewardRules = "基础达标(24h湿度恢复+覆盖≥90%)：500 ECO；超额补水(湿度稳定在正常范围上限80%以上)：额外+150 ECO；覆盖面积不足90%：扣减20%~40%生态币；未在72小时内完成：扣减25%生态币。";
            triggerRule = "连续3天土壤湿度日均值低于30%";
            bountyBase = 500;
        } else if ("草原生态".equals(category)) {
            message = entityName + "土壤干旱胁迫：连续3天土壤湿度低于30%，牧草根系层失水，返青率下降，需要大面积补水喷淋。";
            actionDetail = "喷灌补水：采用移动喷灌设备对退化草场实施喷淋；浅沟集水：沿等高线开挖浅沟，间距3米，深度15cm；植被覆盖：补播耐旱草种，增加地表覆盖度。";
            actionParameters = "单次喷灌量不少于" + (int)(waterMl * 0.8) + "ml/㎡，优先在清晨进行；浅沟长度覆盖整片草场，连续无中断；作业周期：72小时内完成首轮补水。";
            acceptanceCriteria = "测点：均匀布设6个土壤湿度监测点(0-15cm土层)，土壤湿度传感器精度±2%RH；阈值：24h土壤湿度均值≥30%RH；面积核验：喷灌覆盖面积≥85%，浅沟完整连通。";
            baselineData = "连续3天土壤湿度日均值低于30%，今日缺水差值" + String.format("%.1f", deficit) + "%";
            rewardRules = "基础达标400 ECO；全域补水+湿度稳定≥70%RH：额外+120 ECO；覆盖面积不足85%：扣减20%~40%生态币。";
            triggerRule = "连续3天土壤湿度日均值低于30%";
            bountyBase = 400;
        } else {
            message = entityName + "土壤已连续3天偏干，根系吸水困难，请立即补水。";
            actionDetail = "向受影响种植带/根区实施滴灌或喷灌，单次补水量不少于" + waterMl + "ml，优先在清晨6:00-8:00或傍晚17:00-19:00进行，避免正午蒸发；补水后铺设覆盖物减少蒸发。";
            actionParameters = "滴灌流速控制在2-3ml/秒，单次补水时长不少于2小时；覆盖物厚度≥3cm，覆盖面积≥90%；分2-3次小幅补水，避免土壤过湿。";
            acceptanceCriteria = "测点：均匀布设5个土壤湿度监测点(0-20cm土层)，土壤湿度传感器精度±2%RH；阈值：24h土壤湿度均值恢复至30%-70%RH；外观核验：覆盖物完整无裸露，拍照留档。";
            baselineData = "连续3天土壤湿度日均值低于30%，今日缺水差值" + String.format("%.1f", deficit) + "%";
            rewardRules = "基础达标400 ECO；超额补水(湿度稳定在正常范围上限80%以上)：额外+120 ECO；覆盖面积不足90%：扣减15%~35%生态币；未在72小时内完成：扣减20%生态币。";
            triggerRule = "连续3天土壤湿度日均值低于30%";
            bountyBase = 400;
        }

        return new AppealContent(message, actionDetail, actionParameters,
                "24h土壤湿度均值恢复至30%-70%RH", acceptanceCriteria, baselineData,
                rewardRules, triggerRule, severity, deadlineHours, bountyBase);
    }

    private AppealContent generateQuietRequestAppeal(String category, String entityName, java.util.Random random) {
        int peak = 60 + random.nextInt(20);
        double avg = 50 + random.nextDouble() * 15;
        String severity = peak > 70 ? "HIGH" : "MEDIUM";
        int deadlineHours = "HIGH".equals(severity) ? 24 : 48;
        double bountyBase = 350;

        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules, triggerRule;

        if ("海洋生态".equals(category) || "滨海湿地".equals(category)) {
            message = entityName + "近岸栖息地夜间噪声过载：距离礁群50m水域夜间持续高分贝，礁栖鱼类、两栖繁殖蛙类作息紊乱，幼体存活率下降，需全域分区域降噪管控。";
            actionDetail = "施工管控：22:00-06:00全域暂停爆破、打桩、重型船舶作业；设备关停：沿岸100m范围内所有大功率射灯、户外音响、抽水机全部断电；人流隔离：珊瑚礁敏感岸线30m范围设置隔离围栏；隔音补强：近岸施工点位加装隔音屏障。";
            actionParameters = "仅允许静音巡检船(发动机噪音≤45dB)通行，单日静音时长满8小时；渔船禁止鸣笛、大功率探照；安排值守人员每2小时巡逻一次；隔音屏障高度≥2m，隔音降噪量≥15dB；隔音屏障沿敏感岸线连续封闭无缺口。";
            acceptanceCriteria = "测点规范：距离珊瑚礁核心区50m水面，架设水下噪声检测仪(精度±0.5dB)，夜间22:00-06:00全程记录；硬性阈值：任意瞬时噪声峰值≤55dB，任意连续2小时区间均值≤50dB；违规扣分项：出现重型机械施工、持续鸣笛，单次噪声超标1小时以上直接判定任务不合格；辅助核验：夜间监控记录无人员闯入敏感区、大功率设备断电记录完整、隔音屏障覆盖率100%拍照留档。";
            baselineData = "改造前夜间噪声峰值" + peak + "dB，均值" + String.format("%.1f", avg) + "dB，超过阈值60dB";
            rewardRules = "基础达标(全时段管控+噪声指标合格)：945 ECO；进阶优化：增设水下隔音浮障、将噪声均值稳定控制在45dB以内，额外+320 ECO；局部管控失效(单侧岸线噪声超标)：扣减40%生态币；噪声峰值持续超过55dB：按超标时长扣减15%~50%生态币。";
            triggerRule = "夜间噪声峰值达" + peak + "dB，超过阈值60dB";
            bountyBase = 630;
        } else {
            message = entityName + "夜间声环境持续超标，周边物种休息节律受到干扰，请恢复安静。";
            actionDetail = "22:00-06:00暂停施工、关闭高功率照明与音响设备，引导人流远离敏感栖息地，必要时设置临时隔音屏障。";
            actionParameters = "隔音屏障高度≥2m，隔音降噪量≥15dB；夜间值守人员每2小时巡逻一次；仅允许静音设备作业(噪音≤45dB)；敏感区域周边30m设置隔离围栏。";
            acceptanceCriteria = "测点：敏感区域周边1m高度噪声仪(二级声级计)，22:00-06:00连续监测；标准：瞬时峰值≤55dB，任意连续2小时均值≤50dB；辅助核验：夜间监控记录无人员闯入敏感区、隔音屏障覆盖率100%拍照留档。";
            baselineData = "改造前夜间噪声峰值" + peak + "dB，均值" + String.format("%.1f", avg) + "dB";
            rewardRules = "基础达标350 ECO；进阶优化：增设完整隔音屏障、噪声均值稳定≤45dB，额外+120 ECO；噪声超标持续1小时以上：扣减30%~50%生态币；隔音屏障覆盖率不足：按缺失面积扣减15%~30%生态币。";
            triggerRule = "夜间噪声峰值达" + peak + "dB，超过阈值60dB";
            bountyBase = 350;
        }

        return new AppealContent(message, actionDetail, actionParameters,
                "夜间(22:00-06:00)噪声峰值≤55dB，且连续2小时均值≤50dB", acceptanceCriteria,
                baselineData, rewardRules, triggerRule, severity, deadlineHours, bountyBase);
    }

    private AppealContent generateWaterQualityAppeal(String category, String entityName, java.util.Random random) {
        double avg = 5.5 + random.nextDouble() * 4.0;
        boolean acidic = avg < 7.0;
        String severity = Math.abs(avg - 7.5) > 1.0 ? "HIGH" : "MEDIUM";
        int deadlineHours = "HIGH".equals(severity) ? 24 : 48;
        double bountyBase = 600;

        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules, triggerRule;

        if ("海洋生态".equals(category) || "滨海湿地".equals(category)) {
            message = acidic
                    ? entityName + "海域水体酸化：pH均值" + String.format("%.2f", avg) + "低于正常范围，珊瑚钙化速率下降，幼体存活率降低，需要紧急碱化调节。"
                    : entityName + "海域水体偏碱：pH均值" + String.format("%.2f", avg) + "高于正常范围，氨氮毒性升高，珊瑚白化风险增加，需要酸性缓冲调节。";
            actionDetail = acidic
                    ? "投放贝壳粉/碳酸钙缓释剂，开启海水循环曝气系统，每日监测礁盘内外pH变化，分3-4次小幅调节。"
                    : "增加表层海水交换，引入红树林净化水流，开启酸性缓冲模块，48小时内完成首轮调节。";
            actionParameters = acidic
                    ? "贝壳粉投放量10-15kg/100㎡，均匀撒布礁盘区域；循环曝气时长不少于8小时/天；每次调节幅度≤0.3pH单位。"
                    : "海水交换率≥20%/天；红树林净化水流引入量≥50m³/小时；每次调节幅度≤0.3pH单位。";
            acceptanceCriteria = "测点：礁盘核心区、入水口、边缘区各布设1个pH监测点，精度±0.05pH单位；阈值：24h水体pH稳定在6.5-8.5，波动幅度≤0.3pH；辅助核验：缓冲剂投放记录完整、曝气系统运行正常，拍照留档。";
            baselineData = "改造前24h水质pH均值" + String.format("%.2f", avg) + "，正常范围6.5-8.5，偏离幅度" + String.format("%.2f", Math.abs(avg - 7.5)) + "pH";
            rewardRules = "基础达标(24h稳定在正常范围)：900 ECO；进阶优化(pH稳定在正常范围中值±0.2)：额外+300 ECO；调节幅度超标：扣减20%~40%生态币；未在48小时内完成：扣减30%生态币。";
            triggerRule = "近24h水质pH均值" + String.format("%.2f", avg) + "，正常范围6.5-8.5";
            bountyBase = 900;
        } else {
            message = acidic
                    ? entityName + "水体酸化：pH均值" + String.format("%.2f", avg) + "低于正常范围，水生植物根系吸收受阻，底栖动物繁殖率下降，需要碱化调节。"
                    : entityName + "水体偏碱：pH均值" + String.format("%.2f", avg) + "高于正常范围，营养盐溶解度降低，浮游生物群落失衡，需要酸性缓冲调节。";
            actionDetail = acidic
                    ? "投放石灰质缓冲剂，开启湿地循环泵，每日监测入水口与核心区pH，分2-3次小幅调节。"
                    : "减少碱性输入源，增加淡水置换，开启酸性缓冲模块，48小时内完成首轮调节。";
            actionParameters = acidic
                    ? "石灰质缓冲剂投放量5-8kg/100㎡；循环泵运行时长不少于6小时/天；每次调节幅度≤0.2pH单位。"
                    : "淡水置换率≥15%/天；每次调节幅度≤0.2pH单位；48小时内完成首轮调节。";
            acceptanceCriteria = "测点：入水口、湖心、出水口各布设1个pH监测点，精度±0.05pH单位；阈值：24h水体pH稳定在6.5-8.5，波动幅度≤0.2pH；辅助核验：调节记录完整、设备运行正常，拍照留档。";
            baselineData = "改造前24h水质pH均值" + String.format("%.2f", avg) + "，正常范围6.5-8.5，偏离幅度" + String.format("%.2f", Math.abs(avg - 7.5)) + "pH";
            rewardRules = "基础达标600 ECO；进阶优化(pH稳定在正常范围中值±0.15)：额外+180 ECO；调节幅度超标：扣减20%~40%生态币；未在48小时内完成：扣减25%生态币。";
            triggerRule = "近24h水质pH均值" + String.format("%.2f", avg) + "，正常范围6.5-8.5";
            bountyBase = 600;
        }

        return new AppealContent(message, actionDetail, actionParameters,
                "24h水体pH稳定在6.5-8.5，波动幅度≤0.3pH", acceptanceCriteria,
                baselineData, rewardRules, triggerRule, severity, deadlineHours, bountyBase);
    }

    private AppealContent generateHeatStressAppeal(String category, String entityName, java.util.Random random) {
        double avg = 35 + random.nextDouble() * 10;
        String severity = avg > 40 ? "HIGH" : "MEDIUM";
        int deadlineHours = "HIGH".equals(severity) ? 24 : 36;
        double bountyBase = 350;

        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules, triggerRule;

        if ("城市生态".equals(category)) {
            message = entityName + "热胁迫：6h空气温度均值" + String.format("%.1f", avg) + "°C超过正常上限，冠层温度升高导致蒸腾加剧，草本植物叶片萎蔫，需要紧急降温防护。";
            actionDetail = "遮阳网覆盖：在立体绿化种植槽上方搭建遮阳网；雾化降温：开启喷淋雾化系统；地表覆盖：在种植床表面铺设3cm厚覆盖物；灌溉调整：将灌溉调整至清晨6:00-8:00与傍晚17:00-19:00。";
            actionParameters = "遮阳网覆盖率≥90%，遮光率≥60%；雾化喷淋频率每15分钟一次，单次持续5分钟；覆盖物厚度≥3cm，覆盖面积≥95%；日灌溉量≥500ml/㎡。";
            acceptanceCriteria = "测点：均匀布设5个空气温度监测点(冠层2个+地表2个+中心1个)，自动温湿度记录仪精度±0.1°C；阈值：6h空气温度均值≤35°C，地表温度≤35°C；外观核验：遮阳网完整无破损、喷淋系统运行正常，拍照留档。";
            baselineData = "改造前6h空气温度均值" + String.format("%.1f", avg) + "°C，超过上限35°C，超标幅度" + String.format("%.1f", avg - 35) + "°C";
            rewardRules = "基础达标(6h均值达标+覆盖≥90%)：350 ECO；进阶优化(均值≤33°C+遮阳网全覆盖)：额外+120 ECO；覆盖面积不足90%：扣减15%~35%生态币；未在36小时内完成：扣减20%生态币。";
            triggerRule = "近6h空气温度均值" + String.format("%.1f", avg) + "°C，超过上限35°C";
            bountyBase = 350;
        } else if ("森林生态".equals(category)) {
            message = entityName + "热胁迫：6h空气温度均值" + String.format("%.1f", avg) + "°C超过正常上限，林内温度升高导致蒸腾加剧，幼苗死亡率上升，需要降温保湿。";
            actionDetail = "林隙遮阳：在林隙区域搭建临时遮阳网；喷雾降温：在幼林区设置喷雾装置；地表覆盖：在幼苗根部铺设5cm厚落叶覆盖物；溪流补水：确保林间溪流流量充足。";
            actionParameters = "遮阳网覆盖林隙区域≥80%，遮光率≥50%；喷雾频率每20分钟一次，单次持续5分钟；落叶覆盖厚度≥5cm，覆盖面积≥90%；溪流流量≥10L/分钟。";
            acceptanceCriteria = "测点：均匀布设6个空气温度监测点(林冠2个+林下2个+林隙2个)，自动温湿度记录仪精度±0.1°C；阈值：6h空气温度均值≤35°C，土壤湿度≥30%RH；外观核验：遮阳网、喷雾装置运行正常，拍照留档。";
            baselineData = "改造前6h空气温度均值" + String.format("%.1f", avg) + "°C，超过上限35°C";
            rewardRules = "基础达标400 ECO；进阶优化(均值≤34°C+林下湿度≥80%RH)：额外+120 ECO；覆盖面积不足80%：扣减20%~40%生态币。";
            triggerRule = "近6h空气温度均值" + String.format("%.1f", avg) + "°C，超过上限35°C";
            bountyBase = 400;
        } else if ("荒漠生态".equals(category)) {
            message = entityName + "热胁迫：6h空气温度均值" + String.format("%.1f", avg) + "°C超过正常上限，地表温度过高导致幼苗灼伤，胡杨蒸腾加剧，需要降温防护。";
            actionDetail = "遮阳网覆盖：在绿洲核心区搭建遮阳网；雾化降温：在幼苗区设置喷雾装置；地表覆盖：在根部铺设5cm厚秸秆覆盖物；补水灌溉：增加清晨灌溉频次。";
            actionParameters = "遮阳网覆盖率≥85%，遮光率≥60%；喷雾频率每15分钟一次，单次持续5分钟；秸秆覆盖厚度≥5cm，覆盖面积≥90%；日灌溉量≥400ml/㎡。";
            acceptanceCriteria = "测点：均匀布设5个空气温度监测点(绿洲边缘2个+中心2个+幼苗区1个)，自动温湿度记录仪精度±0.1°C；阈值：6h空气温度均值≤35°C；外观核验：遮阳网完整无破损、喷雾装置运行正常，拍照留档。";
            baselineData = "改造前6h空气温度均值" + String.format("%.1f", avg) + "°C，超过上限35°C";
            rewardRules = "基础达标400 ECO；进阶优化(均值≤33°C+全覆盖)：额外+120 ECO；覆盖面积不足85%：扣减15%~35%生态币；未在36小时内完成：扣减20%生态币。";
            triggerRule = "近6h空气温度均值" + String.format("%.1f", avg) + "°C，超过上限35°C";
            bountyBase = 400;
        } else {
            message = entityName + "感到燥热：冠层与地表温度持续偏高，蒸腾加剧，叶片可能出现萎蔫。";
            actionDetail = "启用遮阳网/雾化降温，增加土壤表层覆盖物，将灌溉调整至清晨与傍晚，对幼体与浅根植物优先降温。";
            actionParameters = "遮阳网覆盖率≥85%，遮光率≥50%；雾化喷淋频率每20分钟一次；覆盖物厚度≥3cm，覆盖面积≥90%；日灌溉量≥300ml/㎡，优先在清晨与傍晚进行。";
            acceptanceCriteria = "测点：均匀布设5个空气温度监测点(四角各1个+中心1个)，自动温湿度记录仪精度±0.1°C；阈值：6h空气温度均值≤35°C；外观核验：遮阳网完整无破损、喷淋系统运行正常，拍照留档。";
            baselineData = "改造前6h空气温度均值" + String.format("%.1f", avg) + "°C，超过上限35°C，超标幅度" + String.format("%.1f", avg - 35) + "°C";
            rewardRules = "基础达标300 ECO；进阶优化(均值≤33°C+全覆盖)：额外+90 ECO；覆盖面积不足85%：扣减15%~35%生态币；未在36小时内完成：扣减20%生态币。";
            triggerRule = "近6h空气温度均值" + String.format("%.1f", avg) + "°C，超过上限35°C";
            bountyBase = 300;
        }

        return new AppealContent(message, actionDetail, actionParameters,
                "6h空气温度均值≤35°C", acceptanceCriteria, baselineData,
                rewardRules, triggerRule, severity, deadlineHours, bountyBase);
    }

    private AppealContent generateColdStressAppeal(String category, String entityName, java.util.Random random) {
        double avg = 5 + random.nextDouble() * 10;
        String severity = avg < 8 ? "HIGH" : "MEDIUM";
        int deadlineHours = "HIGH".equals(severity) ? 12 : 24;
        double bountyBase = 250;

        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules, triggerRule;

        if ("城市生态".equals(category)) {
            message = entityName + "地表持续低温胁迫：地表6cm土层日均温低于12°C，草本幼苗萌发停滞、浅根灌木根系冻伤，需要针对性分层保温防护。";
            actionDetail = "保温毡铺设：所有立体绿化种植槽、楼顶花池全覆盖；防风屏障布设：迎风侧每2米设置1道高度1.2m防风围挡；局部根区保温：盆栽植物每盆包裹保温棉厚度2cm，浅根苗木根部堆5cm秸秆保温层。";
            actionParameters = "保温毡厚度≥3mm，铺设重叠搭接宽度≥10cm，总面积按任务地块实测面积核算；防风围挡高度1.2m，风口处额外加密50%屏障，地块边缘连续封闭无缺口；盆栽根部保温棉厚度2cm，浅根苗木根部堆5cm秸秆保温层；作业时效：当日18:00前完成全部保温布设，夜间20:00-次日04:00持续防护。";
            acceptanceCriteria = "测温规则：地块均匀布设5个固定测温点位(四角各1个+中心1个)，仪器精度±0.1°C(自动温湿度记录仪)，连续采集6小时(20:00-02:00)空气温度；合格阈值：6小时全部点位算术平均值≥15.0°C，任意单点瞬时温度不得低于12°C；外观核验：保温毡覆盖率100%、防风屏障完整无破损、盆栽根区保温包裹到位，拍照留档(每10㎡至少1张)。";
            baselineData = "改造前6h空气温度均值" + String.format("%.1f", avg) + "°C，低于正常下限15°C";
            rewardRules = "基础达标(覆盖100%地块+温度合格)：250 ECO；超额防护(保温毡加厚至5mm/屏障加密一倍，连续8h温度≥16°C)：额外+80 ECO；局部地块未全覆盖、单点长期低于12°C：按缺失面积扣减30%~70%生态币；未在18:00前完成布设：扣减15%生态币。";
            triggerRule = "近6h空气温度均值" + String.format("%.1f", avg) + "°C，低于下限15°C";
            bountyBase = 250;
        } else if ("草原生态".equals(category)) {
            message = entityName + "浅层草根、一年生牧草遭遇夜间低温冻害，地表10cm土层温度长期低于10°C，牧草返青推迟，啮齿类动物巢穴失温，需要大面积防风保温。";
            actionDetail = "防风障：沿草原坡地、风口每3米布设一道1m高秸秆防风带，连片草场形成网格防风体系；地表保温：退化牧草区域覆盖5cm厚干草秸秆；洼地幼苗保护：低洼易冻区域幼苗堆土5cm根部保温，成片搭设简易保温网。";
            actionParameters = "防风带高度1m，每3米一道，连片草场形成网格防风体系；干草秸秆覆盖厚度5cm，覆盖面积不少于任务地块80%；低洼易冻区域幼苗堆土5cm根部保温；作业周期：日落前1小时完成全部布设，覆盖整夜低温时段(约8-10小时)。";
            acceptanceCriteria = "测点：草原地块均匀布设6个土层测温点(坡顶2个+坡中2个+洼地2个)，空气温湿度记录仪连续采集6小时夜间气温；阈值：6小时空气均值≥15°C，地下10cm土层温度不低于12°C；面积核验：防风带、干草覆盖面积达标，无大面积裸露冻土(裸露面积不超过总面积5%)；外观核验：防风带完整无倒伏、秸秆覆盖均匀，拍照留档。";
            baselineData = "改造前6h空气温度均值" + String.format("%.1f", avg) + "°C，地表10cm土层温度低于12°C";
            rewardRules = "基础达标250 ECO；全域秸秆全覆盖+土层恒温≥14°C，额外+75 ECO；覆盖面积不足80%：扣减20%~40%生态币；裸露冻土面积超过5%：扣减10%~25%生态币；提前完成布设(日落前2小时)：额外+30 ECO。";
            triggerRule = "近6h空气温度均值" + String.format("%.1f", avg) + "°C，地表10cm土层温度低于12°C";
            bountyBase = 250;
        } else if ("寒带生态".equals(category)) {
            message = entityName + "表层持续低温胁迫：地表15cm冻土层日均温低于-5°C，苔原植被休眠期延长，冻土微生物活性下降，需要加固保温防护。";
            actionDetail = "表层覆盖：冻原敏感区域覆盖8cm厚泥炭藓+落叶混合物；防风栅栏：沿融冻前沿每5米设置1道1.5m高防风栅栏；热融湖防护：热融湖周边20m范围铺设防渗透保温膜。";
            actionParameters = "泥炭藓+落叶混合物覆盖厚度≥8cm，覆盖面积不少于敏感区域90%；防风栅栏高度1.5m，间距5米，连续封闭；防渗透保温膜厚度≥0.5mm，覆盖热融湖周边20m范围；作业时效：当日16:00前完成全部布设。";
            acceptanceCriteria = "测点：冻原地块均匀布设6个深层测温点(5cm/10cm/15cm各2个)，自动温湿度记录仪连续采集8小时夜间气温；阈值：8小时空气均值≥5°C，地下15cm土层温度不低于-2°C；面积核验：泥炭覆盖、防风栅栏、保温膜覆盖率达标；外观核验：无大面积破损，拍照留档。";
            baselineData = "改造前6h空气温度均值" + String.format("%.1f", avg) + "°C，地表15cm土层温度低于-5°C";
            rewardRules = "基础达标400 ECO；全域覆盖+土层恒温≥0°C，额外+150 ECO；覆盖面积不足90%：扣减25%~50%生态币。";
            triggerRule = "近6h空气温度均值" + String.format("%.1f", avg) + "°C，地表15cm土层温度低于-5°C";
            bountyBase = 400;
        } else {
            message = entityName + "感到寒冷：低温抑制萌发与代谢，部分物种进入胁迫状态，需要保温。";
            actionDetail = "覆盖保温毡、设置风障，减少夜间辐射降温；对盆栽与浅根区采取局部保温措施。";
            actionParameters = "保温毡厚度≥3mm，铺设重叠搭接宽度≥10cm，覆盖面积不少于任务地块90%；防风围挡高度≥1.0m，间距≤3米；作业时效：当日18:00前完成全部保温布设，夜间20:00-次日04:00持续防护。";
            acceptanceCriteria = "测温规则：地块均匀布设5个固定测温点位(四角各1个+中心1个)，仪器精度±0.1°C(自动温湿度记录仪)，连续采集6小时(20:00-02:00)空气温度；合格阈值：6小时全部点位算术平均值≥15.0°C，任意单点瞬时温度不得低于12°C；外观核验：保温毡覆盖率≥90%、防风屏障完整无破损，拍照留档。";
            baselineData = "改造前6h空气温度均值" + String.format("%.1f", avg) + "°C，低于正常下限15°C";
            rewardRules = "基础达标250 ECO；超额防护(保温毡加厚至5mm/屏障加密一倍，连续8h温度≥16°C)：额外+60 ECO；局部地块未全覆盖、单点长期低于12°C：按缺失面积扣减30%~70%生态币；未在18:00前完成布设：扣减15%生态币。";
            triggerRule = "近6h空气温度均值" + String.format("%.1f", avg) + "°C，低于下限15°C";
            bountyBase = 250;
        }

        return new AppealContent(message, actionDetail, actionParameters,
                "6小时空气温度均值≥15.0°C", acceptanceCriteria, baselineData,
                rewardRules, triggerRule, severity, deadlineHours, bountyBase);
    }

    private AppealContent generateDryAirAppeal(String category, String entityName, java.util.Random random) {
        double avg = 20 + random.nextDouble() * 20;
        String severity = avg < 25 ? "HIGH" : "MEDIUM";
        int deadlineHours = "HIGH".equals(severity) ? 24 : 48;
        double bountyBase = 200;

        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules, triggerRule;

        if ("荒漠生态".equals(category)) {
            message = entityName + "空气干燥胁迫：24h空气湿度均值" + String.format("%.1f", avg) + "%RH低于正常下限，叶片气孔失水加快，胡杨幼苗蒸腾加剧，需要紧急加湿防护。";
            actionDetail = "雾化加湿：在绿洲核心区设置雾化喷淋装置；地表洒水：增加地面洒水频次；植被保湿：在水洼周边种植耐旱保湿植物；覆盖保湿：在幼苗根部铺设5cm厚秸秆覆盖物。";
            actionParameters = "雾化喷淋频率每10分钟一次，单次持续3分钟；地面洒水频次不少于6次/天，单次洒水量≥200ml/㎡；秸秆覆盖厚度≥5cm，覆盖面积≥90%；保湿植物种植密度≥5株/㎡。";
            acceptanceCriteria = "测点：均匀布设5个空气湿度监测点(水洼周边2个+绿洲边缘2个+中心1个)，自动温湿度记录仪精度±2%RH；阈值：24h空气湿度均值≥40%RH；外观核验：喷淋装置运行正常、覆盖物完整，拍照留档。";
            baselineData = "改造前24h空气湿度均值 " + String.format("%.1f", avg) + "%RH，低于 40%RH，差值" + String.format("%.1f", 40 - avg) + "%RH";
            rewardRules = "基础达标(24h湿度达标+覆盖≥90%)：300 ECO；进阶优化(湿度≥80%RH+全覆盖)：额外+100 ECO；覆盖面积不足90%：扣减15%~30%生态币；未在48小时内完成：扣减20%生态币。";
            triggerRule = "近24h空气湿度均值" + String.format("%.1f", avg) + "%RH，低于40%RH";
            bountyBase = 300;
        } else if ("城市生态".equals(category)) {
            message = entityName + "空气干燥：24h空气湿度均值" + String.format("%.1f", avg) + "%RH低于正常下限，叶片气孔失水加快，花粉活力下降，蜜蜂访花行为减少，需要加湿调节。";
            actionDetail = "叶面喷雾：开启立体绿化喷淋系统；地面加湿：增加种植床周边地面洒水频次；保湿覆盖：在种植槽表面铺设3cm厚覆盖物；通风调节：在冠层区域设置微风循环装置。";
            actionParameters = "喷淋频率每15分钟一次，单次持续5分钟；地面洒水频次不少于4次/天，单次洒水量≥150ml/㎡；覆盖物厚度≥3cm，覆盖面积≥95%；微风循环装置运行时长不少于8小时/天。";
            acceptanceCriteria = "测点：均匀布设4个空气湿度监测点(冠层2个+地表2个)，自动温湿度记录仪精度±2%RH；阈值：24h空气湿度均值≥40%RH；外观核验：喷淋系统运行正常、覆盖物完整，拍照留档。";
            baselineData = "改造前24h空气湿度均值 " + String.format("%.1f", avg) + "%RH，低于 40%RH";
            rewardRules = "基础达标250 ECO；进阶优化(湿度≥50%RH+全覆盖)：额外+80 ECO；覆盖面积不足95%：扣减10%~25%生态币。";
            triggerRule = "近24h空气湿度均值" + String.format("%.1f", avg) + "%RH，低于40%RH";
            bountyBase = 250;
        } else {
            message = entityName + "空气太干燥：气孔失水加快，花粉活力与昆虫访花行为可能下降，请为我加湿。";
            actionDetail = "开启叶面喷雾或区域加湿器，增加地面洒水频次，在冠层周围补植保湿植被；铺设覆盖物减少水分蒸发。";
            actionParameters = "喷淋频率每20分钟一次，单次持续5分钟；地面洒水频次不少于3次/天，单次洒水量≥100ml/㎡；覆盖物厚度≥3cm，覆盖面积≥85%；作业周期：48小时内完成加湿布设。";
            acceptanceCriteria = "测点：均匀布设4个空气湿度监测点(四角各1个)，自动温湿度记录仪精度±2%RH；阈值：24h空气湿度均值≥40%RH；外观核验：喷淋系统运行正常、覆盖物完整，拍照留档。";
            baselineData = "改造前24h空气湿度均值 " + String.format("%.1f", avg) + "%RH，低于 40%RH，差值" + String.format("%.1f", 40 - avg) + "%RH";
            rewardRules = "基础达标200 ECO；进阶优化(湿度≥80%RH+全覆盖)：额外+60 ECO；覆盖面积不足85%：扣减15%~30%生态币；未在48小时内完成：扣减20%生态币。";
            triggerRule = "近24h空气湿度均值" + String.format("%.1f", avg) + "%RH，低于40%RH";
            bountyBase = 200;
        }

        return new AppealContent(message, actionDetail, actionParameters,
                "24h空气湿度均值≥40%RH", acceptanceCriteria, baselineData,
                rewardRules, triggerRule, severity, deadlineHours, bountyBase);
    }

    private AppealContent generateNoiseDaytimeAppeal(String category, String entityName, java.util.Random random) {
        int peak = 70 + random.nextInt(20);
        double avg = 60 + random.nextDouble() * 15;
        String severity = peak > 80 ? "HIGH" : "MEDIUM";
        int deadlineHours = "HIGH".equals(severity) ? 12 : 24;
        double bountyBase = 300;

        return new AppealContent(
                entityName + "日间声环境持续超标：峰值" + peak + "dB，均值" + String.format("%.1f", avg) + "dB，周边物种觅食与繁殖行为受干扰，请降低噪声。",
                "施工时段调整：将高噪声作业调整至夜间22:00-06:00以外时段；设备降噪：重型设备加装隔音罩，运输车辆限速并禁止鸣笛；屏障设置：沿敏感区域设置隔音屏障；人流管控：引导游客远离核心栖息区。",
                "隔音屏障高度≥2.5m，隔音降噪量≥20dB；重型设备隔音罩覆盖率100%；运输车辆时速≤30km/h，禁止鸣笛；敏感区域周边50m设置禁入标识；作业时效：24小时内完成降噪布设。",
                "日间(06:00-22:00)噪声峰值≤75dB，连续2小时均值≤65dB",
                "测点：敏感区域周边1m高度噪声仪(二级声级计)，日间06:00-22:00连续监测；标准：瞬时峰值≤75dB，任意连续2小时均值≤65dB；辅助核验：隔音屏障覆盖率100%、重型设备隔音罩到位、禁入标识清晰，拍照留档。",
                "改造前日间噪声峰值" + peak + "dB，均值" + String.format("%.1f", avg) + "dB",
                "基础达标300 ECO；进阶优化(均值≤60dB+完整隔音屏障)：额外+100 ECO；噪声超标持续1小时以上：扣减20%~40%生态币；隔音屏障覆盖率不足：按缺失面积扣减15%~30%生态币。",
                "日间噪声峰值达" + peak + "dB，超过阈值75dB",
                severity, deadlineHours, bountyBase);
    }

    private record AppealContent(
            String message,
            String actionDetail,
            String actionParameters,
            String measurableTarget,
            String acceptanceCriteria,
            String baselineData,
            String rewardRules,
            String triggerRule,
            String severity,
            int deadlineHours,
            double bountyBase
    ) {}
}
