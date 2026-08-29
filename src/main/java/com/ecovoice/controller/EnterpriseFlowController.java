package com.ecovoice.controller;

import com.ecovoice.dto.EnterpriseTaskDto;
import com.ecovoice.dto.ResourceExchangeRequest;
import com.ecovoice.service.EnterpriseFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 企业生态流通控制器
 * 整合：任务评估 → 生态币发放 → 智能合约还款 → 资源兑换
 */
@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class EnterpriseFlowController {

    private final EnterpriseFlowService enterpriseFlowService;

    /**
     * 获取企业已接取的任务列表（含评估状态）
     */
    @GetMapping("/tasks/{humanNodeId}")
    public ResponseEntity<List<EnterpriseTaskDto>> getEnterpriseTasks(@PathVariable Long humanNodeId) {
        return ResponseEntity.ok(enterpriseFlowService.getEnterpriseTasks(humanNodeId));
    }

    /**
     * 自动化评估任务完成情况并发放生态币
     * 基于传感器实时数据对比基线数据和验收标准进行评估
     */
    @PostMapping("/tasks/{appealId}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateTask(@PathVariable Long appealId) {
        return ResponseEntity.ok(enterpriseFlowService.evaluateAndReward(appealId));
    }

    /**
     * 任务结束后还款给智能合约
     */
    @PostMapping("/tasks/{appealId}/repay")
    public ResponseEntity<Map<String, Object>> repayToSmartContract(@PathVariable Long appealId) {
        return ResponseEntity.ok(enterpriseFlowService.repayToSmartContract(appealId));
    }

    /**
     * 企业使用生态币兑换资源
     */
    @PostMapping("/exchange/{humanNodeId}")
    public ResponseEntity<Map<String, Object>> exchangeResource(
            @PathVariable Long humanNodeId,
            @RequestBody ResourceExchangeRequest request) {
        return ResponseEntity.ok(enterpriseFlowService.exchangeResource(humanNodeId, request));
    }

    /**
     * 获取企业可兑换的资源目录
     */
    @GetMapping("/resources/catalog")
    public ResponseEntity<List<Map<String, Object>>> getResourceCatalog() {
        return ResponseEntity.ok(enterpriseFlowService.getResourceCatalog());
    }
}