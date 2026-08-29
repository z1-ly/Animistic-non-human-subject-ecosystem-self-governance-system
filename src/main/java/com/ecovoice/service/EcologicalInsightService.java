package com.ecovoice.service;

import com.ecovoice.config.DatabaseLockManager;
import com.ecovoice.domain.*;
import com.ecovoice.dto.*;
import com.ecovoice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EcologicalInsightService {

    private static final double NIGHT_NOISE_THRESHOLD = 60.0;
    private static final int CONSECUTIVE_DRY_DAYS = 3;

    private final NaturalEntityRepository entityRepository;
    private final VitalRecordService vitalRecordService;
    private final NatureAppealRepository appealRepository;
    private final EmotionLogRepository emotionLogRepository;
    @Lazy
    private final BountyService bountyService;

    public EntityInsightDto getInsight(Long entityId) {
        NaturalEntity entity = entityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("自然体不存在"));
        return EntityInsightDto.builder()
                .emotion(computeEmotion(entity))
                .appeals(listAppeals(entityId))
                .logs(listLogs(entityId, 30))
                .build();
    }

    public List<NatureAppealDto> listAppeals(Long entityId) {
        return bountyService.listAppealsWithClaimant(entityId);
    }

    public List<EmotionLogDto> listLogs(Long entityId, int limit) {
        return emotionLogRepository.findByEntityIdOrderByCreatedAtDesc(entityId, PageRequest.of(0, limit))
                .stream()
                .map(EmotionLogDto::from)
                .toList();
    }

    @Transactional
    public void analyzeAll() {
        entityRepository.findAll().forEach(e -> analyzeEntity(e.getId()));
    }

    @Transactional
    public void refreshAllAppeals() {
        List<NatureAppeal> appeals = appealRepository.findAll();
        appeals.forEach(a -> {
            a.setActive(false);
            appealRepository.save(a);
        });
        entityRepository.findAll().forEach(e -> analyzeEntity(e.getId()));
    }

    @Transactional
    public void refreshEntityAppeals(Long entityId) {
        List<NatureAppeal> appeals = appealRepository.findByEntityIdAndActiveTrueOrderByCreatedAtDesc(entityId);
        appeals.forEach(a -> {
            a.setActive(false);
            appealRepository.save(a);
        });
        analyzeEntity(entityId);
    }

    @Transactional
    public void analyzeEntity(Long entityId) {
        synchronized (DatabaseLockManager.DB_WRITE_LOCK) {
            NaturalEntity entity = entityRepository.findById(entityId).orElse(null);
            if (entity == null) {
                return;
            }
            detectAppeals(entity);
            persistEmotionLog(entity);
        }
    }

    private void detectAppeals(NaturalEntity entity) {
        Long id = entity.getId();
        checkSoilThirst(id);
        checkNightNoise(id);
        checkWaterQuality(id);
        checkHeatStress(id);
        checkColdStress(id);
        checkDryAir(id);
    }

    private void checkSoilThirst(Long entityId) {
        if (!consecutiveDaysBelow(entityId, SensorType.SOIL_MOISTURE,
                SensorType.SOIL_MOISTURE.getNormalMin(), CONSECUTIVE_DRY_DAYS)) {
            return;
        }
        double deficit = SensorType.SOIL_MOISTURE.getNormalMin() - dailyAverage(
                entityId, SensorType.SOIL_MOISTURE, LocalDate.now().minusDays(1));
        int waterMl = (int) Math.max(1500, Math.round(deficit * 80));
        String severity = deficit > 15 ? "HIGH" : "MEDIUM";
        
        NaturalEntity entity = entityRepository.findById(entityId).orElse(null);
        String category = entity != null ? entity.getCategory() : "";
        
        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules;
        double bountyBase = 400;
        
        if ("荒漠生态".equals(category)) {
            message = "绿洲土壤持续缺水胁迫：连续" + CONSECUTIVE_DRY_DAYS + "天土壤湿度低于" + SensorType.SOIL_MOISTURE.getNormalMin() + "%，胡杨根系吸水困难，幼苗萎蔫率上升，需要精准补水灌溉。";
            actionDetail = "滴灌补水：沿胡杨林带每5米设置1个滴头，实施精准滴灌；地表覆盖：补水后铺设3cm厚秸秆覆盖物减少蒸发；坎儿井水调配：启用备用坎儿井，增加日供水量。";
            actionParameters = "单次补水量不少于" + waterMl + "ml/㎡，分2-3次进行(清晨6:00-8:00为主)；秸秆覆盖厚度≥3cm，覆盖面积不少于灌溉区域90%；滴灌持续时长不少于4小时/次。";
            acceptanceCriteria = "测点：均匀布设5个土壤湿度监测点(0-20cm土层)，土壤湿度传感器精度±2%RH；阈值：24h土壤湿度均值恢复至" + SensorType.SOIL_MOISTURE.getNormalMin() + "%-" + SensorType.SOIL_MOISTURE.getNormalMax() + "%RH；外观核验：秸秆覆盖完整无裸露，滴灌设施运行正常，拍照留档。";
            baselineData = "连续" + CONSECUTIVE_DRY_DAYS + "天土壤湿度日均值低于" + SensorType.SOIL_MOISTURE.getNormalMin() + "%，今日缺水差值" + String.format("%.1f", deficit) + "%";
            rewardRules = "基础达标(24h湿度恢复+覆盖≥90%)：500 ECO；超额补水(湿度稳定在正常范围上限80%以上)：额外+150 ECO；覆盖面积不足90%：扣减20%~40%生态币；未在72小时内完成：扣减25%生态币。";
            bountyBase = 500;
        } else if ("草原生态".equals(category)) {
            message = "草原土壤干旱胁迫：连续" + CONSECUTIVE_DRY_DAYS + "天土壤湿度低于" + SensorType.SOIL_MOISTURE.getNormalMin() + "%，牧草根系层失水，返青率下降，需要大面积补水喷淋。";
            actionDetail = "喷灌补水：采用移动喷灌设备对退化草场实施喷淋；浅沟集水：沿等高线开挖浅沟，间距3米，深度15cm；植被覆盖：补播耐旱草种，增加地表覆盖度。";
            actionParameters = "单次喷灌量不少于" + (int)(waterMl * 0.8) + "ml/㎡，优先在清晨进行；浅沟长度覆盖整片草场，连续无中断；作业周期：72小时内完成首轮补水。";
            acceptanceCriteria = "测点：均匀布设6个土壤湿度监测点(0-15cm土层)，土壤湿度传感器精度±2%RH；阈值：24h土壤湿度均值≥" + SensorType.SOIL_MOISTURE.getNormalMin() + "%RH；面积核验：喷灌覆盖面积≥85%，浅沟完整连通。";
            baselineData = "连续" + CONSECUTIVE_DRY_DAYS + "天土壤湿度日均值低于" + SensorType.SOIL_MOISTURE.getNormalMin() + "%，今日缺水差值" + String.format("%.1f", deficit) + "%";
            rewardRules = "基础达标400 ECO；全域补水+湿度稳定≥" + SensorType.SOIL_MOISTURE.getNormalMax() + "%RH：额外+120 ECO；覆盖面积不足85%：扣减20%~40%生态币。";
            bountyBase = 400;
        } else {
            message = "我口渴了：土壤已连续 " + CONSECUTIVE_DRY_DAYS + " 天偏干，根系吸水困难，请立即补水。";
            actionDetail = "向受影响种植带/根区实施滴灌或喷灌，单次补水量不少于 " + waterMl + " ml，优先在清晨 6:00-8:00 或傍晚 17:00-19:00 进行，避免正午蒸发；补水后铺设覆盖物减少蒸发。";
            actionParameters = "滴灌流速控制在2-3ml/秒，单次补水时长不少于2小时；覆盖物厚度≥3cm，覆盖面积≥90%；分2-3次小幅补水，避免土壤过湿。";
            acceptanceCriteria = "测点：均匀布设5个土壤湿度监测点(0-20cm土层)，土壤湿度传感器精度±2%RH；阈值：24h土壤湿度均值恢复至" + SensorType.SOIL_MOISTURE.getNormalMin() + "%-" + SensorType.SOIL_MOISTURE.getNormalMax() + "%RH；外观核验：覆盖物完整无裸露，拍照留档。";
            baselineData = "连续" + CONSECUTIVE_DRY_DAYS + "天土壤湿度日均值低于" + SensorType.SOIL_MOISTURE.getNormalMin() + "%，今日缺水差值" + String.format("%.1f", deficit) + "%";
            rewardRules = "基础达标400 ECO；超额补水(湿度稳定在正常范围上限80%以上)：额外+120 ECO；覆盖面积不足90%：扣减15%~35%生态币；未在72小时内完成：扣减20%生态币。";
            bountyBase = 400;
        }
        
        createAppeal(entityId, AppealType.THIRST,
                message,
                actionDetail,
                actionParameters,
                "24h 土壤湿度均值恢复至 " + SensorType.SOIL_MOISTURE.getNormalMin()
                        + "%-" + SensorType.SOIL_MOISTURE.getNormalMax() + "%RH",
                acceptanceCriteria,
                baselineData,
                rewardRules,
                "连续" + CONSECUTIVE_DRY_DAYS + "天土壤湿度日均值低于"
                        + SensorType.SOIL_MOISTURE.getNormalMin() + "%",
                severity, 72, bountyBase);
    }

    private void checkNightNoise(Long entityId) {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        List<VitalSignRecord> records = vitalRecordService
                .findByEntityIdAndSensorTypeAndRecordedAtAfter(entityId, SensorType.SOUND_DECIBEL, since);
        boolean nightExceed = records.stream()
                .anyMatch(r -> isNightHour(r.getRecordedAt()) && r.getValue() > NIGHT_NOISE_THRESHOLD);
        if (!nightExceed) {
            return;
        }
        double peak = records.stream()
                .filter(r -> isNightHour(r.getRecordedAt()))
                .mapToDouble(VitalSignRecord::getValue)
                .max()
                .orElse(NIGHT_NOISE_THRESHOLD);
        double avg = records.stream()
                .filter(r -> isNightHour(r.getRecordedAt()))
                .mapToDouble(VitalSignRecord::getValue)
                .average()
                .orElse(NIGHT_NOISE_THRESHOLD);
        String severity = peak > 70 ? "HIGH" : "MEDIUM";
        
        NaturalEntity entity = entityRepository.findById(entityId).orElse(null);
        String category = entity != null ? entity.getCategory() : "";
        
        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules;
        double bountyBase = 350;
        
        if ("海洋生态".equals(category) || "滨海湿地".equals(category)) {
            message = "珊瑚礁近岸栖息地夜间噪声过载：距离礁群50m水域夜间持续高分贝，礁栖鱼类、两栖繁殖蛙类作息紊乱，幼体存活率下降，需全域分区域降噪管控。";
            actionDetail = "施工管控：22:00-06:00全域暂停爆破、打桩、重型船舶作业；设备关停：沿岸100m范围内所有大功率射灯、户外音响、抽水机全部断电；人流隔离：珊瑚礁敏感岸线30m范围设置隔离围栏；隔音补强：近岸施工点位加装隔音屏障。";
            actionParameters = "仅允许静音巡检船(发动机噪音≤45dB)通行，单日静音时长满8小时；渔船禁止鸣笛、大功率探照；安排值守人员每2小时巡逻一次；隔音屏障高度≥2m，隔音降噪量≥15dB；隔音屏障沿敏感岸线连续封闭无缺口。";
            acceptanceCriteria = "测点规范：距离珊瑚礁核心区50m水面，架设水下噪声检测仪(精度±0.5dB)，夜间22:00-06:00全程记录；硬性阈值：任意瞬时噪声峰值≤55dB，任意连续2小时区间均值≤50dB；违规扣分项：出现重型机械施工、持续鸣笛，单次噪声超标1小时以上直接判定任务不合格；辅助核验：夜间监控记录无人员闯入敏感区、大功率设备断电记录完整、隔音屏障覆盖率100%拍照留档。";
            baselineData = "改造前夜间噪声峰值 " + Math.round(peak) + "dB，均值 " + String.format("%.1f", avg) + "dB，超过阈值 " + (int) NIGHT_NOISE_THRESHOLD + "dB";
            rewardRules = "基础达标(全时段管控+噪声指标合格)：945 ECO；进阶优化：增设水下隔音浮障、将噪声均值稳定控制在45dB以内，额外+320 ECO；局部管控失效(单侧岸线噪声超标)：扣减40%生态币；噪声峰值持续超过55dB：按超标时长扣减15%~50%生态币。";
            bountyBase = 630;
        } else if ("荒漠生态".equals(category)) {
            message = "沙漠绿洲核心水洼栖息地夜间人为噪音超标，荒漠蜥蜴、夜行鸟类、蛙类觅食休憩受干扰，水源周边小型生物逃离，绿洲生物密度下降。";
            actionDetail = "时段管控：22:00-06:00绿洲水源周边150m范围内禁止越野车、发电机、高音喇叭作业；设备管控：营地照明全部更换低光暖光灯，禁止强光探照扫射水域；物理隔音：水源上风方向搭建高1.5m沙土隔音墙；人流管控：水源50m外设夜间禁入标识。";
            actionParameters = "音响设备22点前全部断电；沙土隔音墙长度覆盖整片水域边缘(连续无缺口)；夜间露营人群统一安置在200m以外区域；安排值守人员每2小时巡逻一次。";
            acceptanceCriteria = "测点：绿洲水洼中心旁1m高度噪声仪(二级声级计，精度±1dB)，22:00-06:00连续监测；标准：瞬时峰值≤55dB，任意连续2小时均值≤50dB；额外生物辅助量化：任务完成3天后，水洼周边夜间可见原生生物数量不低于作业前基线80%(可视化辅助核验)；物理核验：隔音沙墙完整无破损、禁入标识到位，拍照留档。";
            baselineData = "改造前夜间噪声峰值 " + Math.round(peak) + "dB，均值 " + String.format("%.1f", avg) + "dB";
            rewardRules = "基础达标630 ECO；搭建完整环形隔音沙墙、均值稳定≤45dB，额外+210 ECO；生物数量低于基线80%：扣减25%生态币；隔音墙覆盖率不足100%：按缺失长度扣减15%~35%生态币。";
            bountyBase = 420;
        } else {
            message = "夜间声环境持续超标，周边物种休息节律受到干扰，请恢复安静。";
            actionDetail = "22:00-06:00暂停施工、关闭高功率照明与音响设备，引导人流远离敏感栖息地，必要时设置临时隔音屏障。";
            actionParameters = "隔音屏障高度≥2m，隔音降噪量≥15dB；夜间值守人员每2小时巡逻一次；仅允许静音设备作业(噪音≤45dB)；敏感区域周边30m设置隔离围栏。";
            acceptanceCriteria = "测点：敏感区域周边1m高度噪声仪(二级声级计)，22:00-06:00连续监测；标准：瞬时峰值≤55dB，任意连续2小时均值≤50dB；辅助核验：夜间监控记录无人员闯入敏感区、隔音屏障覆盖率100%拍照留档。";
            baselineData = "改造前夜间噪声峰值 " + Math.round(peak) + "dB，均值 " + String.format("%.1f", avg) + "dB";
            rewardRules = "基础达标350 ECO；进阶优化：增设完整隔音屏障、噪声均值稳定≤45dB，额外+120 ECO；噪声超标持续1小时以上：扣减30%~50%生态币；隔音屏障覆盖率不足：按缺失面积扣减15%~30%生态币。";
            bountyBase = 350;
        }
        
        createAppeal(entityId, AppealType.QUIET_REQUEST,
                message,
                actionDetail,
                actionParameters,
                "夜间(22:00-06:00) 噪声峰值 ≤55dB，且连续 2 小时均值 ≤50dB",
                acceptanceCriteria,
                baselineData,
                rewardRules,
                "夜间噪声峰值达 " + Math.round(peak) + "dB，超过阈值 "
                        + (int) NIGHT_NOISE_THRESHOLD + "dB",
                severity, 48, bountyBase);
    }

    private void checkWaterQuality(Long entityId) {
        double avg = recentAverage(entityId, SensorType.WATER_PH, 24);
        if (Double.isNaN(avg)) {
            return;
        }
        SensorType type = SensorType.WATER_PH;
        if (avg >= type.getNormalMin() && avg <= type.getNormalMax()) {
            return;
        }
        boolean acidic = avg < type.getNormalMin();
        
        NaturalEntity entity = entityRepository.findById(entityId).orElse(null);
        String category = entity != null ? entity.getCategory() : "";
        
        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules;
        double bountyBase = 600;
        
        if ("海洋生态".equals(category) || "滨海湿地".equals(category)) {
            message = acidic
                    ? "珊瑚礁海域水体酸化：pH均值" + String.format("%.2f", avg) + "低于正常范围，珊瑚钙化速率下降，幼体存活率降低，需要紧急碱化调节。"
                    : "珊瑚礁海域水体偏碱：pH均值" + String.format("%.2f", avg) + "高于正常范围，氨氮毒性升高，珊瑚白化风险增加，需要酸性缓冲调节。";
            actionDetail = acidic
                    ? "投放贝壳粉/碳酸钙缓释剂，开启海水循环曝气系统，每日监测礁盘内外pH变化，分3-4次小幅调节。"
                    : "增加表层海水交换，引入红树林净化水流，开启酸性缓冲模块，48小时内完成首轮调节。";
            actionParameters = acidic
                    ? "贝壳粉投放量10-15kg/100㎡，均匀撒布礁盘区域；循环曝气时长不少于8小时/天；每次调节幅度≤0.3pH单位。"
                    : "海水交换率≥20%/天；红树林净化水流引入量≥50m³/小时；每次调节幅度≤0.3pH单位。";
            acceptanceCriteria = "测点：礁盘核心区、入水口、边缘区各布设1个pH监测点，精度±0.05pH单位；阈值：24h水体pH稳定在" + type.getNormalMin() + "-" + type.getNormalMax() + "，波动幅度≤0.3pH；辅助核验：缓冲剂投放记录完整、曝气系统运行正常，拍照留档。";
            baselineData = "改造前24h水质pH均值 " + String.format("%.2f", avg) + "，正常范围 " + type.getNormalMin() + "-" + type.getNormalMax() + "，偏离幅度" + String.format("%.2f", Math.abs(avg - (type.getNormalMin() + type.getNormalMax()) / 2)) + "pH";
            rewardRules = "基础达标(24h稳定在正常范围)：900 ECO；进阶优化(pH稳定在正常范围中值±0.2)：额外+300 ECO；调节幅度超标：扣减20%~40%生态币；未在48小时内完成：扣减30%生态币。";
            bountyBase = 900;
        } else if ("河口湿地".equals(category) || "淡水湿地".equals(category)) {
            message = acidic
                    ? "湿地水体酸化：pH均值" + String.format("%.2f", avg) + "低于正常范围，水生植物根系吸收受阻，底栖动物繁殖率下降，需要碱化调节。"
                    : "湿地水体偏碱：pH均值" + String.format("%.2f", avg) + "高于正常范围，营养盐溶解度降低，浮游生物群落失衡，需要酸性缓冲调节。";
            actionDetail = acidic
                    ? "投放石灰质缓冲剂，开启湿地循环泵，每日监测入水口与核心区pH，分2-3次小幅调节。"
                    : "减少碱性输入源，增加淡水置换，开启酸性缓冲模块，48小时内完成首轮调节。";
            actionParameters = acidic
                    ? "石灰质缓冲剂投放量5-8kg/100㎡；循环泵运行时长不少于6小时/天；每次调节幅度≤0.2pH单位。"
                    : "淡水置换率≥15%/天；每次调节幅度≤0.2pH单位；48小时内完成首轮调节。";
            acceptanceCriteria = "测点：入水口、湖心、出水口各布设1个pH监测点，精度±0.05pH单位；阈值：24h水体pH稳定在" + type.getNormalMin() + "-" + type.getNormalMax() + "，波动幅度≤0.2pH；辅助核验：调节记录完整、设备运行正常，拍照留档。";
            baselineData = "改造前24h水质pH均值 " + String.format("%.2f", avg) + "，正常范围 " + type.getNormalMin() + "-" + type.getNormalMax() + "，偏离幅度" + String.format("%.2f", Math.abs(avg - (type.getNormalMin() + type.getNormalMax()) / 2)) + "pH";
            rewardRules = "基础达标600 ECO；进阶优化(pH稳定在正常范围中值±0.15)：额外+180 ECO；调节幅度超标：扣减20%~40%生态币；未在48小时内完成：扣减25%生态币。";
            bountyBase = 600;
        } else {
            message = acidic
                    ? "我的水体偏酸：水生生物钙化受阻，底栖群落面临胁迫，请调节水质。"
                    : "我的水体偏碱：氨氮毒性升高，浮游生物群落失衡，请调节水质。";
            actionDetail = acidic
                    ? "投放适量石灰质缓冲剂或开启循环曝气，每日监测入水口与核心区pH，分2-3次小幅调节，避免剧烈波动。"
                    : "减少碱性输入源，增加淡水置换或开启酸性缓冲模块，48小时内完成首轮调节并持续监测。";
            actionParameters = acidic
                    ? "石灰质缓冲剂投放量3-5kg/100㎡；循环曝气时长不少于6小时/天；每次调节幅度≤0.2pH单位，48小时内完成调节。"
                    : "淡水置换率≥10%/天；每次调节幅度≤0.2pH单位；48小时内完成首轮调节。";
            acceptanceCriteria = "测点：均匀布设3个pH监测点(入水口、核心区、出水口)，精度±0.05pH单位；阈值：24h水体pH稳定在" + type.getNormalMin() + "-" + type.getNormalMax() + "，波动幅度≤0.3pH；辅助核验：调节记录完整、设备运行正常，拍照留档。";
            baselineData = "改造前24h水质pH均值 " + String.format("%.2f", avg) + "，正常范围 " + type.getNormalMin() + "-" + type.getNormalMax() + "，偏离幅度" + String.format("%.2f", Math.abs(avg - (type.getNormalMin() + type.getNormalMax()) / 2)) + "pH";
            rewardRules = "基础达标600 ECO；进阶优化(pH稳定在正常范围中值±0.2)：额外+180 ECO；调节幅度超标：扣减20%~40%生态币；未在48小时内完成：扣减25%生态币。";
            bountyBase = 600;
        }
        
        createAppeal(entityId, AppealType.WATER_QUALITY, message, actionDetail, actionParameters,
                "24h 水体 pH 稳定在 " + type.getNormalMin() + "-" + type.getNormalMax(), acceptanceCriteria,
                baselineData, rewardRules,
                "近24h 水质 pH 均值 " + String.format("%.2f", avg)
                        + "，正常范围 " + type.getNormalMin() + "-" + type.getNormalMax(),
                "HIGH", 48, bountyBase);
    }

    private void checkHeatStress(Long entityId) {
        double avg = recentAverage(entityId, SensorType.AIR_TEMPERATURE, 6);
        if (Double.isNaN(avg) || avg <= SensorType.AIR_TEMPERATURE.getNormalMax()) {
            return;
        }
        String severity = avg > SensorType.AIR_TEMPERATURE.getNormalMax() + 5 ? "HIGH" : "MEDIUM";
        
        NaturalEntity entity = entityRepository.findById(entityId).orElse(null);
        String category = entity != null ? entity.getCategory() : "";
        
        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules;
        double bountyBase = 300;
        
        if ("城市生态".equals(category)) {
            message = "城市绿岛热胁迫：6h空气温度均值" + String.format("%.1f", avg) + "°C超过正常上限，冠层温度升高导致蒸腾加剧，草本植物叶片萎蔫，需要紧急降温防护。";
            actionDetail = "遮阳网覆盖：在立体绿化种植槽上方搭建遮阳网；雾化降温：开启喷淋雾化系统；地表覆盖：在种植床表面铺设3cm厚覆盖物；灌溉调整：将灌溉调整至清晨6:00-8:00与傍晚17:00-19:00。";
            actionParameters = "遮阳网覆盖率≥90%，遮光率≥60%；雾化喷淋频率每15分钟一次，单次持续5分钟；覆盖物厚度≥3cm，覆盖面积≥95%；日灌溉量≥500ml/㎡。";
            acceptanceCriteria = "测点：均匀布设5个空气温度监测点(冠层2个+地表2个+中心1个)，自动温湿度记录仪精度±0.1℃；阈值：6h空气温度均值≤" + SensorType.AIR_TEMPERATURE.getNormalMax() + "°C，地表温度≤35℃；外观核验：遮阳网完整无破损、喷淋系统运行正常，拍照留档。";
            baselineData = "改造前6h空气温度均值 " + String.format("%.1f", avg) + "°C，超过上限 " + SensorType.AIR_TEMPERATURE.getNormalMax() + "°C，超标幅度" + String.format("%.1f", avg - SensorType.AIR_TEMPERATURE.getNormalMax()) + "°C";
            rewardRules = "基础达标(6h均值达标+覆盖≥90%)：350 ECO；进阶优化(均值≤" + (SensorType.AIR_TEMPERATURE.getNormalMax() - 2) + "°C+遮阳网全覆盖)：额外+120 ECO；覆盖面积不足90%：扣减15%~35%生态币；未在36小时内完成：扣减20%生态币。";
            bountyBase = 350;
        } else if ("森林生态".equals(category)) {
            message = "森林热胁迫：6h空气温度均值" + String.format("%.1f", avg) + "°C超过正常上限，林内温度升高导致蒸腾加剧，幼苗死亡率上升，需要降温保湿。";
            actionDetail = "林隙遮阳：在林隙区域搭建临时遮阳网；喷雾降温：在幼林区设置喷雾装置；地表覆盖：在幼苗根部铺设5cm厚落叶覆盖物；溪流补水：确保林间溪流流量充足。";
            actionParameters = "遮阳网覆盖林隙区域≥80%，遮光率≥50%；喷雾频率每20分钟一次，单次持续5分钟；落叶覆盖厚度≥5cm，覆盖面积≥90%；溪流流量≥10L/分钟。";
            acceptanceCriteria = "测点：均匀布设6个空气温度监测点(林冠2个+林下2个+林隙2个)，自动温湿度记录仪精度±0.1℃；阈值：6h空气温度均值≤" + SensorType.AIR_TEMPERATURE.getNormalMax() + "°C，土壤湿度≥" + SensorType.SOIL_MOISTURE.getNormalMin() + "%RH；外观核验：遮阳网、喷雾装置运行正常，拍照留档。";
            baselineData = "改造前6h空气温度均值 " + String.format("%.1f", avg) + "°C，超过上限 " + SensorType.AIR_TEMPERATURE.getNormalMax() + "°C";
            rewardRules = "基础达标400 ECO；进阶优化(均值≤" + (SensorType.AIR_TEMPERATURE.getNormalMax() - 1) + "°C+林下湿度≥" + SensorType.AIR_HUMIDITY.getNormalMax() + "%RH)：额外+120 ECO；覆盖面积不足80%：扣减20%~40%生态币。";
            bountyBase = 400;
        } else {
            message = "我感到燥热：冠层与地表温度持续偏高，蒸腾加剧，叶片可能出现萎蔫。";
            actionDetail = "启用遮阳网/雾化降温，增加土壤表层覆盖物，将灌溉调整至清晨与傍晚，对幼体与浅根植物优先降温。";
            actionParameters = "遮阳网覆盖率≥85%，遮光率≥50%；雾化喷淋频率每20分钟一次；覆盖物厚度≥3cm，覆盖面积≥90%；日灌溉量≥300ml/㎡，优先在清晨与傍晚进行。";
            acceptanceCriteria = "测点：均匀布设5个空气温度监测点(四角各1个+中心1个)，自动温湿度记录仪精度±0.1℃；阈值：6h空气温度均值≤" + SensorType.AIR_TEMPERATURE.getNormalMax() + "°C；外观核验：遮阳网完整无破损、喷淋系统运行正常，拍照留档。";
            baselineData = "改造前6h空气温度均值 " + String.format("%.1f", avg) + "°C，超过上限 " + SensorType.AIR_TEMPERATURE.getNormalMax() + "°C，超标幅度" + String.format("%.1f", avg - SensorType.AIR_TEMPERATURE.getNormalMax()) + "°C";
            rewardRules = "基础达标300 ECO；进阶优化(均值≤" + (SensorType.AIR_TEMPERATURE.getNormalMax() - 2) + "°C+全覆盖)：额外+90 ECO；覆盖面积不足85%：扣减15%~35%生态币；未在36小时内完成：扣减20%生态币。";
            bountyBase = 300;
        }
        
        createAppeal(entityId, AppealType.HEAT_STRESS,
                message,
                actionDetail,
                actionParameters,
                "6h 空气温度均值降至 ≤" + SensorType.AIR_TEMPERATURE.getNormalMax() + "°C",
                acceptanceCriteria,
                baselineData,
                rewardRules,
                "近6h 空气温度均值 " + String.format("%.1f", avg) + "°C，超过上限 "
                        + SensorType.AIR_TEMPERATURE.getNormalMax() + "°C",
                severity, 36, bountyBase);
    }

    private void checkColdStress(Long entityId) {
        double avg = recentAverage(entityId, SensorType.AIR_TEMPERATURE, 6);
        if (Double.isNaN(avg) || avg >= SensorType.AIR_TEMPERATURE.getNormalMin()) {
            return;
        }
        NaturalEntity entity = entityRepository.findById(entityId).orElse(null);
        String category = entity != null ? entity.getCategory() : "";
        
        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules;
        
        if ("城市生态".equals(category)) {
            message = "地表持续低温胁迫：地表6cm土层日均温低于12℃，草本幼苗萌发停滞、浅根灌木根系冻伤，需要针对性分层保温防护。";
            actionDetail = "保温毡铺设：所有立体绿化种植槽、楼顶花池全覆盖；防风屏障布设：迎风侧每2米设置1道高度1.2m防风围挡；局部根区保温：盆栽植物每盆包裹保温棉厚度2cm，浅根苗木根部堆5cm秸秆保温层。";
            actionParameters = "保温毡厚度≥3mm，铺设重叠搭接宽度≥10cm，总面积按任务地块实测面积核算；防风围挡高度1.2m，风口处额外加密50%屏障，地块边缘连续封闭无缺口；盆栽根部保温棉厚度2cm，浅根苗木根部堆5cm秸秆保温层；作业时效：当日18:00前完成全部保温布设，夜间20:00-次日04:00持续防护。";
            acceptanceCriteria = "测温规则：地块均匀布设5个固定测温点位(四角各1个+中心1个)，仪器精度±0.1℃(自动温湿度记录仪)，连续采集6小时(20:00-02:00)空气温度；合格阈值：6小时全部点位算术平均值≥15.0℃，任意单点瞬时温度不得低于12℃；外观核验：保温毡覆盖率100%、防风屏障完整无破损、盆栽根区保温包裹到位，拍照留档(每10㎡至少1张)。";
            baselineData = "改造前6h空气温度均值 " + String.format("%.1f", avg) + "°C，低于正常下限 " + SensorType.AIR_TEMPERATURE.getNormalMin() + "°C";
            rewardRules = "基础达标(覆盖100%地块+温度合格)：250 ECO；超额防护(保温毡加厚至5mm/屏障加密一倍，连续8h温度≥16℃)：额外+80 ECO；局部地块未全覆盖、单点长期低于12℃：按缺失面积扣减30%~70%生态币；未在18:00前完成布设：扣减15%生态币。";
        } else if ("草原生态".equals(category)) {
            message = "草原浅层草根、一年生牧草遭遇夜间低温冻害，地表10cm土层温度长期低于10℃，牧草返青推迟，啮齿类动物巢穴失温，需要大面积防风保温。";
            actionDetail = "防风障：沿草原坡地、风口每3米布设一道1m高秸秆防风带，连片草场形成网格防风体系；地表保温：退化牧草区域覆盖5cm厚干草秸秆；洼地幼苗保护：低洼易冻区域幼苗堆土5cm根部保温，成片搭设简易保温网。";
            actionParameters = "防风带高度1m，每3米一道，连片草场形成网格防风体系；干草秸秆覆盖厚度5cm，覆盖面积不少于任务地块80%；低洼易冻区域幼苗堆土5cm根部保温；作业周期：日落前1小时完成全部布设，覆盖整夜低温时段(约8-10小时)。";
            acceptanceCriteria = "测点：草原地块均匀布设6个土层测温点(坡顶2个+坡中2个+洼地2个)，空气温湿度记录仪连续采集6小时夜间气温；阈值：6小时空气均值≥15℃，地下10cm土层温度不低于12℃；面积核验：防风带、干草覆盖面积达标，无大面积裸露冻土(裸露面积不超过总面积5%)；外观核验：防风带完整无倒伏、秸秆覆盖均匀，拍照留档。";
            baselineData = "改造前6h空气温度均值 " + String.format("%.1f", avg) + "°C，地表10cm土层温度低于12℃";
            rewardRules = "基础达标250 ECO；全域秸秆全覆盖+土层恒温≥14℃，额外+75 ECO；覆盖面积不足80%：扣减20%~40%生态币；裸露冻土面积超过5%：扣减10%~25%生态币；提前完成布设(日落前2小时)：额外+30 ECO。";
        } else if ("寒带生态".equals(category)) {
            message = "冻原表层持续低温胁迫：地表15cm冻土层日均温低于-5℃，苔原植被休眠期延长，冻土微生物活性下降，需要加固保温防护。";
            actionDetail = "表层覆盖：冻原敏感区域覆盖8cm厚泥炭藓+落叶混合物；防风栅栏：沿融冻前沿每5米设置1道1.5m高防风栅栏；热融湖防护：热融湖周边20m范围铺设防渗透保温膜。";
            actionParameters = "泥炭藓+落叶混合物覆盖厚度≥8cm，覆盖面积不少于敏感区域90%；防风栅栏高度1.5m，间距5米，连续封闭；防渗透保温膜厚度≥0.5mm，覆盖热融湖周边20m范围；作业时效：当日16:00前完成全部布设。";
            acceptanceCriteria = "测点：冻原地块均匀布设6个深层测温点(5cm/10cm/15cm各2个)，自动温湿度记录仪连续采集8小时夜间气温；阈值：8小时空气均值≥5℃，地下15cm土层温度不低于-2℃；面积核验：泥炭覆盖、防风栅栏、保温膜覆盖率达标；外观核验：无大面积破损，拍照留档。";
            baselineData = "改造前6h空气温度均值 " + String.format("%.1f", avg) + "°C，地表15cm土层温度低于-5℃";
            rewardRules = "基础达标400 ECO；全域覆盖+土层恒温≥0℃，额外+150 ECO；覆盖面积不足90%：扣减25%~50%生态币。";
        } else {
            message = "我感到寒冷：低温抑制萌发与代谢，部分物种进入胁迫状态，需要保温。";
            actionDetail = "覆盖保温毡、设置风障，减少夜间辐射降温；对盆栽与浅根区采取局部保温措施。";
            actionParameters = "保温毡厚度≥3mm，铺设重叠搭接宽度≥10cm，覆盖面积不少于任务地块90%；防风围挡高度≥1.0m，间距≤3米；作业时效：当日18:00前完成全部保温布设，夜间20:00-次日04:00持续防护。";
            acceptanceCriteria = "测温规则：地块均匀布设5个固定测温点位(四角各1个+中心1个)，仪器精度±0.1℃(自动温湿度记录仪)，连续采集6小时(20:00-02:00)空气温度；合格阈值：6小时全部点位算术平均值≥15.0℃，任意单点瞬时温度不得低于12℃；外观核验：保温毡覆盖率≥90%、防风屏障完整无破损，拍照留档。";
            baselineData = "改造前6h空气温度均值 " + String.format("%.1f", avg) + "°C，低于正常下限 " + SensorType.AIR_TEMPERATURE.getNormalMin() + "°C";
            rewardRules = "基础达标250 ECO；超额防护(保温毡加厚至5mm/屏障加密一倍，连续8h温度≥16℃)：额外+60 ECO；局部地块未全覆盖、单点长期低于12℃：按缺失面积扣减30%~70%生态币；未在18:00前完成布设：扣减15%生态币。";
        }
        
        createAppeal(entityId, AppealType.COLD_STRESS,
                message,
                actionDetail,
                actionParameters,
                "6h 空气温度均值回升至 ≥" + SensorType.AIR_TEMPERATURE.getNormalMin() + "°C",
                acceptanceCriteria,
                baselineData,
                rewardRules,
                "近6h 空气温度均值 " + String.format("%.1f", avg) + "°C，低于下限 "
                        + SensorType.AIR_TEMPERATURE.getNormalMin() + "°C",
                "MEDIUM", 48, 250);
    }

    private void checkDryAir(Long entityId) {
        double avg = recentAverage(entityId, SensorType.AIR_HUMIDITY, 24);
        if (Double.isNaN(avg) || avg >= SensorType.AIR_HUMIDITY.getNormalMin()) {
            return;
        }
        
        NaturalEntity entity = entityRepository.findById(entityId).orElse(null);
        String category = entity != null ? entity.getCategory() : "";
        
        String message, actionDetail, actionParameters, acceptanceCriteria, baselineData, rewardRules;
        double bountyBase = 200;
        
        if ("荒漠生态".equals(category)) {
            message = "荒漠绿洲空气干燥胁迫：24h空气湿度均值" + String.format("%.1f", avg) + "%RH低于正常下限，叶片气孔失水加快，胡杨幼苗蒸腾加剧，需要紧急加湿防护。";
            actionDetail = "雾化加湿：在绿洲核心区设置雾化喷淋装置；地表洒水：增加地面洒水频次；植被保湿：在水洼周边种植耐旱保湿植物；覆盖保湿：在幼苗根部铺设5cm厚秸秆覆盖物。";
            actionParameters = "雾化喷淋频率每10分钟一次，单次持续3分钟；地面洒水频次不少于6次/天，单次洒水量≥200ml/㎡；秸秆覆盖厚度≥5cm，覆盖面积≥90%；保湿植物种植密度≥5株/㎡。";
            acceptanceCriteria = "测点：均匀布设5个空气湿度监测点(水洼周边2个+绿洲边缘2个+中心1个)，自动温湿度记录仪精度±2%RH；阈值：24h空气湿度均值≥" + SensorType.AIR_HUMIDITY.getNormalMin() + "%RH；外观核验：喷淋装置运行正常、覆盖物完整，拍照留档。";
            baselineData = "改造前24h空气湿度均值 " + String.format("%.1f", avg) + "%RH，低于 " + SensorType.AIR_HUMIDITY.getNormalMin() + "%RH，差值" + String.format("%.1f", SensorType.AIR_HUMIDITY.getNormalMin() - avg) + "%RH";
            rewardRules = "基础达标(24h湿度达标+覆盖≥90%)：300 ECO；进阶优化(湿度≥" + SensorType.AIR_HUMIDITY.getNormalMax() + "%RH+全覆盖)：额外+100 ECO；覆盖面积不足90%：扣减15%~30%生态币；未在48小时内完成：扣减20%生态币。";
            bountyBase = 300;
        } else if ("城市生态".equals(category)) {
            message = "城市绿岛空气干燥：24h空气湿度均值" + String.format("%.1f", avg) + "%RH低于正常下限，叶片气孔失水加快，花粉活力下降，蜜蜂访花行为减少，需要加湿调节。";
            actionDetail = "叶面喷雾：开启立体绿化喷淋系统；地面加湿：增加种植床周边地面洒水频次；保湿覆盖：在种植槽表面铺设3cm厚覆盖物；通风调节：在冠层区域设置微风循环装置。";
            actionParameters = "喷淋频率每15分钟一次，单次持续5分钟；地面洒水频次不少于4次/天，单次洒水量≥150ml/㎡；覆盖物厚度≥3cm，覆盖面积≥95%；微风循环装置运行时长不少于8小时/天。";
            acceptanceCriteria = "测点：均匀布设4个空气湿度监测点(冠层2个+地表2个)，自动温湿度记录仪精度±2%RH；阈值：24h空气湿度均值≥" + SensorType.AIR_HUMIDITY.getNormalMin() + "%RH；外观核验：喷淋系统运行正常、覆盖物完整，拍照留档。";
            baselineData = "改造前24h空气湿度均值 " + String.format("%.1f", avg) + "%RH，低于 " + SensorType.AIR_HUMIDITY.getNormalMin() + "%RH";
            rewardRules = "基础达标250 ECO；进阶优化(湿度≥" + (SensorType.AIR_HUMIDITY.getNormalMin() + 10) + "%RH+全覆盖)：额外+80 ECO；覆盖面积不足95%：扣减10%~25%生态币。";
            bountyBase = 250;
        } else {
            message = "空气太干燥：气孔失水加快，花粉活力与昆虫访花行为可能下降，请为我加湿。";
            actionDetail = "开启叶面喷雾或区域加湿器，增加地面洒水频次，在冠层周围补植保湿植被；铺设覆盖物减少水分蒸发。";
            actionParameters = "喷淋频率每20分钟一次，单次持续5分钟；地面洒水频次不少于3次/天，单次洒水量≥100ml/㎡；覆盖物厚度≥3cm，覆盖面积≥85%；作业周期：48小时内完成加湿布设。";
            acceptanceCriteria = "测点：均匀布设4个空气湿度监测点(四角各1个)，自动温湿度记录仪精度±2%RH；阈值：24h空气湿度均值≥" + SensorType.AIR_HUMIDITY.getNormalMin() + "%RH；外观核验：喷淋系统运行正常、覆盖物完整，拍照留档。";
            baselineData = "改造前24h空气湿度均值 " + String.format("%.1f", avg) + "%RH，低于 " + SensorType.AIR_HUMIDITY.getNormalMin() + "%RH，差值" + String.format("%.1f", SensorType.AIR_HUMIDITY.getNormalMin() - avg) + "%RH";
            rewardRules = "基础达标200 ECO；进阶优化(湿度≥" + SensorType.AIR_HUMIDITY.getNormalMax() + "%RH+全覆盖)：额外+60 ECO；覆盖面积不足85%：扣减15%~30%生态币；未在48小时内完成：扣减20%生态币。";
            bountyBase = 200;
        }
        
        createAppeal(entityId, AppealType.DRY_AIR,
                message,
                actionDetail,
                actionParameters,
                "24h 空气湿度均值 ≥" + SensorType.AIR_HUMIDITY.getNormalMin() + "%RH",
                acceptanceCriteria,
                baselineData,
                rewardRules,
                "近24h 空气湿度均值 " + String.format("%.1f", avg) + "%RH，低于 "
                        + SensorType.AIR_HUMIDITY.getNormalMin() + "%RH",
                "MEDIUM", 48, bountyBase);
    }

    private void createAppeal(Long entityId, AppealType type, String message,
                              String actionDetail, String actionParameters,
                              String measurableTarget, String acceptanceCriteria,
                              String baselineData, String rewardRules,
                              String triggerRule, String severity,
                              int deadlineHours, double bountyBase) {
        if (appealRepository.findFirstByEntityIdAndAppealTypeAndActiveTrue(entityId, type).isPresent()) {
            return;
        }
        NaturalEntity entity = entityRepository.findById(entityId).orElse(null);
        double multiplier = entity != null && "行星权利主体".equals(entity.getCategory()) ? 10.0 : 1.0;
        if (entity != null && ("海洋生态".equals(entity.getCategory())
                || "滨海湿地".equals(entity.getCategory()))) {
            multiplier = 1.5;
        }
        double bounty = Math.round(bountyBase * multiplier * ("HIGH".equals(severity) ? 1.8 : 1.0));

        NatureAppeal appeal = NatureAppeal.builder()
                .entityId(entityId)
                .appealType(type)
                .message(message)
                .actionDetail(actionDetail)
                .actionParameters(actionParameters)
                .measurableTarget(measurableTarget)
                .acceptanceCriteria(acceptanceCriteria)
                .baselineData(baselineData)
                .rewardRules(rewardRules)
                .triggerRule(triggerRule)
                .severity(severity)
                .deadline(LocalDateTime.now().plusHours(deadlineHours))
                .bountyAmount(bounty)
                .bountyStatus(BountyStatus.OPEN)
                .active(true)
                .build();

        try {
            appeal = appealRepository.save(appeal);
            bountyService.escrowBounty(appeal);
        } catch (Exception e) {
            System.err.println("诉求托管失败: " + type + " - " + e.getMessage());
        }
    }

    private EmotionIndexDto computeEmotion(NaturalEntity entity) {
        Map<SensorType, Double> scores = new LinkedHashMap<>();
        List<String> factors = new ArrayList<>();

        for (SensorType type : SensorType.values()) {
            double avg = recentAverage(entity.getId(), type, 24);
            if (Double.isNaN(avg)) {
                continue;
            }
            double component = sensorComfortScore(type, avg);
            scores.put(type, component);
            if (component < 60) {
                factors.add(describeDiscomfort(type, avg));
            }
        }

        double index = scores.isEmpty() ? 70.0 :
                scores.values().stream().mapToDouble(Double::doubleValue).average().orElse(70.0);
        index = Math.round(index * 10.0) / 10.0;

        EmotionState state = EmotionState.fromScore(index);
        String summary = buildSummary(entity.getName(), index, state, factors);

        return EmotionIndexDto.builder()
                .entityId(entity.getId())
                .entityName(entity.getName())
                .index(index)
                .state(state)
                .stateLabel(state.getLabel())
                .stateColor(state.getColor())
                .summary(summary)
                .factors(factors.isEmpty() ? List.of("各指标处于适宜区间") : factors)
                .evaluatedAt(LocalDateTime.now())
                .build();
    }

    private void persistEmotionLog(NaturalEntity entity) {
        EmotionIndexDto emotion = computeEmotion(entity);
        String narrative = buildNarrativeLog(entity.getName(), emotion);

        Optional<EmotionLog> latest = emotionLogRepository.findFirstByEntityIdOrderByCreatedAtDesc(entity.getId());
        if (latest.isPresent()) {
            EmotionLog prev = latest.get();
            boolean sameState = prev.getEmotionState() == emotion.getState();
            boolean similarIndex = Math.abs(prev.getEmotionIndex() - emotion.getIndex()) < 3.0;
            if (sameState && similarIndex) {
                return;
            }
        }

        emotionLogRepository.save(EmotionLog.builder()
                .entityId(entity.getId())
                .emotionState(emotion.getState())
                .emotionIndex(emotion.getIndex())
                .narrative(narrative)
                .build());
    }

    private String buildNarrativeLog(String name, EmotionIndexDto emotion) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(name).append("】");
        sb.append("生态情绪指数 ").append(emotion.getIndex()).append("，");
        sb.append("当前拟人状态「").append(emotion.getStateLabel()).append("」。");
        if (emotion.getFactors().isEmpty() ||
                (emotion.getFactors().size() == 1 && emotion.getFactors().get(0).contains("适宜"))) {
            sb.append("生命体征整体和谐，我感到很安心。");
        } else {
            sb.append(String.join("；", emotion.getFactors())).append("。");
        }
        return sb.toString();
    }

    private String buildSummary(String name, double index, EmotionState state, List<String> factors) {
        if (factors.isEmpty()) {
            return name + " 当前情绪「" + state.getLabel() + "」，综合指数 " + index;
        }
        return name + " · " + state.getLabel() + " · 指数" + index;
    }

    private String describeDiscomfort(SensorType type, double value) {
        return switch (type) {
            case SOIL_MOISTURE -> value < type.getNormalMin()
                    ? "土壤偏干(" + String.format("%.1f", value) + "%)"
                    : "土壤过湿(" + String.format("%.1f", value) + "%)";
            case WATER_PH -> value < type.getNormalMin()
                    ? "水体偏酸(pH " + String.format("%.2f", value) + ")"
                    : "水体偏碱(pH " + String.format("%.2f", value) + ")";
            case AIR_TEMPERATURE -> value < type.getNormalMin()
                    ? "气温偏低(" + String.format("%.1f", value) + "°C)"
                    : "气温偏高(" + String.format("%.1f", value) + "°C)";
            case AIR_HUMIDITY -> value < type.getNormalMin()
                    ? "空气干燥(" + String.format("%.1f", value) + "%RH)"
                    : "空气潮湿(" + String.format("%.1f", value) + "%RH)";
            case SOUND_DECIBEL -> "声环境嘈杂(" + String.format("%.1f", value) + "dB)";
        };
    }

    private double sensorComfortScore(SensorType type, double value) {
        double min = type.getNormalMin();
        double max = type.getNormalMax();
        double mid = (min + max) / 2.0;
        double halfRange = (max - min) / 2.0;
        if (halfRange <= 0) {
            return 70;
        }
        double deviation = Math.abs(value - mid) / halfRange;
        double score = 100.0 - deviation * 35.0;
        if (value < min - halfRange * 0.5 || value > max + halfRange * 0.5) {
            score -= 20;
        }
        return Math.max(0, Math.min(100, score));
    }

    private boolean consecutiveDaysBelow(Long entityId, SensorType type, double threshold, int days) {
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= days; i++) {
            double avg = dailyAverage(entityId, type, today.minusDays(i));
            if (Double.isNaN(avg) || avg >= threshold) {
                return false;
            }
        }
        return true;
    }

    private double dailyAverage(Long entityId, SensorType type, LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        List<VitalSignRecord> records = vitalRecordService
                .findByEntityIdAndSensorTypeAndRecordedAtBetween(entityId, type, start, end);
        if (records.isEmpty()) {
            return Double.NaN;
        }
        return records.stream().mapToDouble(VitalSignRecord::getValue).average().orElse(Double.NaN);
    }

    private double recentAverage(Long entityId, SensorType type, int hours) {
        List<VitalSignRecord> records = vitalRecordService
                .findByEntityIdAndSensorTypeAndRecordedAtAfter(entityId, type, LocalDateTime.now().minusHours(hours));
        if (records.isEmpty()) {
            return Double.NaN;
        }
        return records.stream().mapToDouble(VitalSignRecord::getValue).average().orElse(Double.NaN);
    }

    private boolean isNightHour(LocalDateTime time) {
        LocalTime t = time.toLocalTime();
        return t.isAfter(LocalTime.of(21, 59)) || t.isBefore(LocalTime.of(6, 0));
    }
}
