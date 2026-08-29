package com.ecovoice.controller;

import com.ecovoice.domain.NaturalEntity;
import com.ecovoice.domain.NetworkConfig;
import com.ecovoice.domain.ProtocolType;
import com.ecovoice.domain.SensorNode;
import com.ecovoice.domain.VitalSignRecord;
import com.ecovoice.dto.*;
import com.ecovoice.service.EcologicalInsightService;
import com.ecovoice.service.SandboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SandboxController {

    private final SandboxService sandboxService;
    private final EcologicalInsightService insightService;

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return sandboxService.platformStats();
    }

    @GetMapping("/entities")
    public List<NaturalEntity> entities() {
        return sandboxService.listEntities();
    }

    @GetMapping("/entities/{id}/dashboard")
    public EntityDashboardDto dashboard(@PathVariable Long id) {
        return sandboxService.getDashboard(id);
    }

    @GetMapping("/entities/{id}/nodes")
    public List<SensorNode> nodes(@PathVariable Long id) {
        return sandboxService.listNodes(id);
    }

    @GetMapping("/protocols")
    public List<ProtocolType> protocols() {
        return sandboxService.listProtocols();
    }

    @GetMapping("/sensor-types")
    public List<com.ecovoice.domain.SensorType> sensorTypes() {
        return sandboxService.listSensorTypes();
    }

    @PostMapping("/network/configure")
    public NetworkConfig configure(@Valid @RequestBody NetworkConfigRequest request) {
        return sandboxService.configureNetwork(request);
    }

    @GetMapping("/vitals/stream")
    public List<VitalSignDto> stream(
            @RequestParam(required = false) Long entityId,
            @RequestParam(defaultValue = "40") int limit) {
        return sandboxService.recentStream(entityId, limit);
    }

    @PostMapping("/ingest/http")
    public VitalSignRecord ingestHttp(@Valid @RequestBody SensorIngestRequest request) {
        return sandboxService.ingest(request, ProtocolType.HTTP_REST);
    }

    @PostMapping("/ingest/mqtt")
    public VitalSignRecord ingestMqtt(@Valid @RequestBody SensorIngestRequest request) {
        return sandboxService.ingest(request, ProtocolType.MQTT);
    }

    @PostMapping("/ingest/coap")
    public VitalSignRecord ingestCoap(@Valid @RequestBody SensorIngestRequest request) {
        return sandboxService.ingest(request, ProtocolType.COAP);
    }

    @PostMapping("/ingest/modbus")
    public VitalSignRecord ingestModbus(@Valid @RequestBody SensorIngestRequest request) {
        return sandboxService.ingest(request, ProtocolType.MODBUS_RTU);
    }

    @PostMapping("/ingest/ws")
    public VitalSignRecord ingestWs(@Valid @RequestBody SensorIngestRequest request) {
        return sandboxService.ingest(request, ProtocolType.WEBSOCKET);
    }

    @PostMapping("/simulate/tick")
    public ResponseEntity<Map<String, String>> simulateTick() {
        sandboxService.simulateRandomReading();
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    @GetMapping("/entities/{id}/insight")
    public EntityInsightDto insight(@PathVariable Long id) {
        return insightService.getInsight(id);
    }

    @GetMapping("/entities/{id}/appeals")
    public List<NatureAppealDto> appeals(@PathVariable Long id) {
        return insightService.listAppeals(id);
    }

    @GetMapping("/entities/{id}/emotion")
    public EmotionIndexDto emotion(@PathVariable Long id) {
        return insightService.getInsight(id).getEmotion();
    }

    @GetMapping("/entities/{id}/emotion/logs")
    public List<EmotionLogDto> emotionLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "30") int limit) {
        return insightService.listLogs(id, limit);
    }

    @PostMapping("/insight/analyze")
    public ResponseEntity<Map<String, String>> analyzeAll() {
        insightService.analyzeAll();
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    @PostMapping("/insight/refresh-appeals")
    public ResponseEntity<Map<String, String>> refreshAppeals() {
        insightService.refreshAllAppeals();
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    @PostMapping("/entities/{id}/appeals/refresh")
    public ResponseEntity<Map<String, String>> refreshEntityAppeals(@PathVariable Long id) {
        insightService.refreshEntityAppeals(id);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }
}
