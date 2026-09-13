package com.ecovoice.service;

import com.ecovoice.domain.*;
import com.ecovoice.dto.*;
import com.ecovoice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EcologicalInsightService {

    private static final double NIGHT_NOISE_THRESHOLD = 60.0;
    private static final int CONSECUTIVE_DRY_DAYS = 3;

    private final NaturalEntityRepository entityRepository;
    private final VitalSignRecordRepository vitalRepository;
    private final NatureAppealRepository appealRepository;
    private final EmotionLogRepository emotionLogRepository;

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
        return appealRepository.findByEntityIdAndActiveTrueOrderByCreatedAtDesc(entityId).stream()
                .map(NatureAppealDto::from)
                .toList();
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
    public void analyzeEntity(Long entityId) {
        NaturalEntity entity = entityRepository.findById(entityId).orElse(null);
        if (entity == null) {
            return;
        }
        detectAppeals(entity);
        persistEmotionLog(entity);
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
        int waterMl = (int) Math.max(1000, Math.round(deficit * 50));
        createAppeal(entityId, AppealType.THIRST,
                "我口渴了，需要" + waterMl + "ml水。",
                "连续" + CONSECUTIVE_DRY_DAYS + "天土壤湿度日均值低于" +
                        SensorType.SOIL_MOISTURE.getNormalMin() + "%",
                deficit > 15 ? "HIGH" : "MEDIUM");
    }

    private void checkNightNoise(Long entityId) {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        List<VitalSignRecord> records = vitalRepository
                .findByEntityIdAndSensorTypeAndRecordedAtAfterOrderByRecordedAtAsc(
                        entityId, SensorType.SOUND_DECIBEL, since);
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
        createAppeal(entityId, AppealType.QUIET_REQUEST,
                "我被吵到了，请求安静环境。",
                "夜间(22:00-06:00)噪声峰值达" + Math.round(peak) + "dB，超过阈值" +
                        (int) NIGHT_NOISE_THRESHOLD + "dB",
                peak > 70 ? "HIGH" : "MEDIUM");
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
        String msg = avg < type.getNormalMin()
                ? "我的水体偏酸了，请求调节水质至适宜pH范围。"
                : "我的水体偏碱了，请求调节水质至适宜pH范围。";
        createAppeal(entityId, AppealType.WATER_QUALITY, msg,
                "近24h水质pH均值" + String.format("%.2f", avg) + "，正常范围" +
                        type.getNormalMin() + "-" + type.getNormalMax(),
                "HIGH");
    }

    private void checkHeatStress(Long entityId) {
        double avg = recentAverage(entityId, SensorType.AIR_TEMPERATURE, 6);
        if (Double.isNaN(avg) || avg <= SensorType.AIR_TEMPERATURE.getNormalMax()) {
            return;
        }
        createAppeal(entityId, AppealType.HEAT_STRESS,
                "我感到燥热，需要遮阳或降温。",
                "近6h空气温度均值" + String.format("%.1f", avg) + "°C，超过上限" +
                        SensorType.AIR_TEMPERATURE.getNormalMax() + "°C",
                avg > SensorType.AIR_TEMPERATURE.getNormalMax() + 5 ? "HIGH" : "MEDIUM");
    }

    private void checkColdStress(Long entityId) {
        double avg = recentAverage(entityId, SensorType.AIR_TEMPERATURE, 6);
        if (Double.isNaN(avg) || avg >= SensorType.AIR_TEMPERATURE.getNormalMin()) {
            return;
        }
        createAppeal(entityId, AppealType.COLD_STRESS,
                "我感到寒冷，需要保温措施。",
                "近6h空气温度均值" + String.format("%.1f", avg) + "°C，低于下限" +
                        SensorType.AIR_TEMPERATURE.getNormalMin() + "°C",
                "MEDIUM");
    }

    private void checkDryAir(Long entityId) {
        double avg = recentAverage(entityId, SensorType.AIR_HUMIDITY, 24);
        if (Double.isNaN(avg) || avg >= SensorType.AIR_HUMIDITY.getNormalMin()) {
            return;
        }
        createAppeal(entityId, AppealType.DRY_AIR,
                "空气太干燥了，请为我加湿。",
                "近24h空气湿度均值" + String.format("%.1f", avg) + "%RH，低于" +
                        SensorType.AIR_HUMIDITY.getNormalMin() + "%RH",
                "MEDIUM");
    }

    private void createAppeal(Long entityId, AppealType type, String message,
                              String triggerRule, String severity) {
        if (appealRepository.findFirstByEntityIdAndAppealTypeAndActiveTrue(entityId, type).isPresent()) {
            return;
        }
        appealRepository.save(NatureAppeal.builder()
                .entityId(entityId)
                .appealType(type)
                .message(message)
                .triggerRule(triggerRule)
                .severity(severity)
                .active(true)
                .build());
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
        List<VitalSignRecord> records = vitalRepository
                .findByEntityIdAndSensorTypeAndRecordedAtBetweenOrderByRecordedAtAsc(
                        entityId, type, start, end);
        if (records.isEmpty()) {
            return Double.NaN;
        }
        return records.stream().mapToDouble(VitalSignRecord::getValue).average().orElse(Double.NaN);
    }

    private double recentAverage(Long entityId, SensorType type, int hours) {
        List<VitalSignRecord> records = vitalRepository
                .findByEntityIdAndSensorTypeAndRecordedAtAfterOrderByRecordedAtAsc(
                        entityId, type, LocalDateTime.now().minusHours(hours));
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
