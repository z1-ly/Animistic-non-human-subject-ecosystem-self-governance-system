package com.ecovoice.service;

import com.ecovoice.config.DatabaseLockManager;
import com.ecovoice.domain.*;
import com.ecovoice.dto.*;
import com.ecovoice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SandboxService {

    private static final DateTimeFormatter CHART_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final NaturalEntityRepository entityRepository;
    private final SensorNodeRepository nodeRepository;
    private final NetworkConfigRepository networkRepository;
    private final VitalRecordService vitalRecordService;
    private final VitalSignStreamService streamService;
    private final EcologicalInsightService insightService;
    private final NatureAppealRepository appealRepository;

    public List<NaturalEntity> listEntities() {
        return entityRepository.findAll();
    }

    public Optional<NaturalEntity> getEntity(Long id) {
        return entityRepository.findById(id);
    }

    public List<SensorNode> listNodes(Long entityId) {
        return nodeRepository.findByEntityId(entityId);
    }

    public List<ProtocolType> listProtocols() {
        return Arrays.asList(ProtocolType.values());
    }

    public List<SensorType> listSensorTypes() {
        return Arrays.asList(SensorType.values());
    }

    @Transactional
    public NetworkConfig configureNetwork(NetworkConfigRequest request) {
        NetworkConfig config = networkRepository.findByEntityId(request.getEntityId())
                .orElse(NetworkConfig.builder().entityId(request.getEntityId()).build());
        config.setProtocol(request.getProtocol());
        config.setEndpoint(resolveEndpoint(request));
        config.setTopic(request.getTopic() != null ? request.getTopic()
                : "ecovoice/entity/" + request.getEntityId() + "/vitals");
        config.setNodeCount((int) nodeRepository.countByEntityId(request.getEntityId()));
        config.setActive(true);
        return networkRepository.save(config);
    }

    private String resolveEndpoint(NetworkConfigRequest request) {
        if (request.getEndpoint() != null && !request.getEndpoint().isBlank()) {
            return request.getEndpoint();
        }
        return switch (request.getProtocol()) {
            case MQTT -> "mqtt://broker.ecovoice.local:1883";
            case HTTP_REST -> "/api/ingest/http";
            case WEBSOCKET -> "/ws/vitals";
            case COAP -> "coap://gateway.ecovoice.local/vitals";
            case MODBUS_RTU -> "COM3@9600";
        };
    }

    @Transactional
    public VitalSignRecord ingest(SensorIngestRequest request, ProtocolType protocol) {
        synchronized (DatabaseLockManager.DB_WRITE_LOCK) {
            NaturalEntity entity = entityRepository.findById(request.getEntityId())
                    .orElseThrow(() -> new IllegalArgumentException("自然体不存在: " + request.getEntityId()));

            SensorNode node = resolveNode(request);
            node.setLastValue(request.getValue());
            node.setOnline(true);
            nodeRepository.save(node);

            VitalSignRecord record = VitalSignRecord.builder()
                    .entityId(entity.getId())
                    .nodeId(node.getId())
                    .sensorType(request.getSensorType())
                    .value(request.getValue())
                    .protocol(protocol)
                    .recordedAt(LocalDateTime.now())
                    .build();
            record = vitalRecordService.save(record);

            streamService.broadcastRecord(record, entity.getName(), node.getNodeCode());
            insightService.analyzeEntity(entity.getId());
            return record;
        }
    }

    private SensorNode resolveNode(SensorIngestRequest request) {
        if (request.getNodeId() != null) {
            return nodeRepository.findById(request.getNodeId())
                    .orElseThrow(() -> new IllegalArgumentException("节点不存在"));
        }
        if (request.getNodeCode() != null) {
            return nodeRepository.findByEntityId(request.getEntityId()).stream()
                    .filter(n -> request.getNodeCode().equals(n.getNodeCode()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("节点编码不存在"));
        }
        return nodeRepository.findByEntityId(request.getEntityId()).stream()
                .filter(n -> n.getSensorType() == request.getSensorType())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到对应传感器节点"));
    }

    public List<VitalSignDto> recentStream(Long entityId, int limit) {
        List<VitalSignRecord> records = entityId == null
                ? vitalRecordService.findByEntityIdOrderByRecordedAtDesc(null, 50)
                : vitalRecordService.findByEntityIdOrderByRecordedAtDesc(entityId, limit);

        Map<Long, String> entityNames = entityRepository.findAll().stream()
                .collect(Collectors.toMap(NaturalEntity::getId, NaturalEntity::getName));
        Map<Long, String> nodeCodes = nodeRepository.findAll().stream()
                .collect(Collectors.toMap(SensorNode::getId, SensorNode::getNodeCode));

        return records.stream()
                .map(r -> VitalSignDto.from(r,
                        entityNames.getOrDefault(r.getEntityId(), "未知"),
                        r.getNodeId() != null ? nodeCodes.getOrDefault(r.getNodeId(), "-") : "-"))
                .toList();
    }

    public EntityDashboardDto getDashboard(Long entityId) {
        NaturalEntity entity = entityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("自然体不存在"));
        List<SensorNode> nodes = nodeRepository.findByEntityId(entityId);
        NetworkConfig network = networkRepository.findByEntityId(entityId).orElse(null);

        List<SensorSnapshotDto> sensors = nodes.stream().map(this::toSnapshot).toList();
        Map<String, List<ChartPointDto>> chartSeries = new LinkedHashMap<>();
        LocalDateTime since = LocalDateTime.now().minusMinutes(30);
        for (SensorType type : SensorType.values()) {
            List<ChartPointDto> points = vitalRecordService
                    .findByEntityIdAndSensorTypeAndRecordedAtAfter(entityId, type, since)
                    .stream()
                    .map(r -> new ChartPointDto(CHART_TIME.format(r.getRecordedAt()), r.getValue()))
                    .toList();
            chartSeries.put(type.name(), points);
        }

        return EntityDashboardDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .category(entity.getCategory())
                .location(entity.getLocation())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .avatarColor(entity.getAvatarColor())
                .protocol(network != null ? network.getProtocol() : null)
                .protocolLabel(network != null ? network.getProtocol().getLabel() : "未配置")
                .nodeCount(nodes.size())
                .totalReadings(vitalRecordService.countByEntity(entityId))
                .sensors(sensors)
                .chartSeries(chartSeries)
                .build();
    }

    private SensorSnapshotDto toSnapshot(SensorNode node) {
        String status = "OFFLINE";
        if (node.isOnline() && node.getLastValue() != null) {
            SensorType type = node.getSensorType();
            double v = node.getLastValue();
            status = (v >= type.getNormalMin() && v <= type.getNormalMax()) ? "NORMAL" : "ALERT";
        }
        return SensorSnapshotDto.builder()
                .nodeId(node.getId())
                .nodeCode(node.getNodeCode())
                .name(node.getName())
                .sensorType(node.getSensorType())
                .label(node.getSensorType().getLabel())
                .unit(node.getSensorType().getUnit())
                .value(node.getLastValue())
                .online(node.isOnline())
                .status(status)
                .position(node.getPosition())
                .build();
    }

    public Map<String, Object> platformStats() {
        long entities = entityRepository.count();
        long nodes = nodeRepository.count();
        long readings = vitalRecordService.count();
        long activeNetworks = networkRepository.findAll().stream().filter(NetworkConfig::isActive).count();
        long activeAppeals = appealRepository.countByActiveTrue();
        return Map.of(
                "entityCount", entities,
                "nodeCount", nodes,
                "readingCount", readings,
                "activeAppeals", activeAppeals,
                "activeNetworks", activeNetworks,
                "protocols", Arrays.stream(ProtocolType.values()).map(ProtocolType::name).toList(),
                "sensorTypes", Arrays.stream(SensorType.values()).map(SensorType::name).toList()
        );
    }

    public void simulateRandomReading() {
        synchronized (DatabaseLockManager.DB_WRITE_LOCK) {
            List<NaturalEntity> entities = entityRepository.findAll();
            if (entities.isEmpty()) return;
            
            for (NaturalEntity entity : entities) {
                List<SensorNode> nodes = nodeRepository.findByEntityId(entity.getId());
                if (nodes.isEmpty()) continue;
                
                ProtocolType protocol = networkRepository.findByEntityId(entity.getId())
                        .map(NetworkConfig::getProtocol)
                        .orElse(ProtocolType.MQTT);

                for (SensorNode node : nodes) {
                    double value = randomValue(node.getSensorType());
                    node.setLastValue(value);
                    node.setOnline(true);
                    nodeRepository.save(node);

                    VitalSignRecord record = VitalSignRecord.builder()
                            .entityId(entity.getId())
                            .nodeId(node.getId())
                            .sensorType(node.getSensorType())
                            .value(value)
                            .protocol(protocol)
                            .recordedAt(LocalDateTime.now())
                            .build();
                    vitalRecordService.save(record);

                    try {
                        streamService.broadcastRecord(record, entity.getName(), node.getNodeCode());
                    } catch (Exception e) {
                        // 忽略广播异常
                    }
                }
            }
        }
    }

    private double randomValue(SensorType type) {
        double min = type.getNormalMin() - (type.getNormalMax() - type.getNormalMin()) * 0.3;
        double max = type.getNormalMax() + (type.getNormalMax() - type.getNormalMin()) * 0.3;
        double v = ThreadLocalRandom.current().nextDouble(min, max);
        return Math.round(v * 100.0) / 100.0;
    }
}
