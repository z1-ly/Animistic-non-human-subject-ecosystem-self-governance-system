package com.ecovoice.controller;

import com.ecovoice.domain.SmartContract;
import com.ecovoice.service.EcoCoinLoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 生态币借贷控制器
 * 提供环境实体借币和还币的API接口
 */
@RestController
@RequestMapping("/api/eco-loan")
@RequiredArgsConstructor
public class EcoCoinLoanController {

    private final EcoCoinLoanService ecoCoinLoanService;

    /**
     * 环境实体借币
     * 
     * @param request 借币请求
     * @return 操作结果
     */
    @PostMapping("/borrow")
    public ResponseEntity<Map<String, Object>> borrowCoins(@RequestBody BorrowRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String message = ecoCoinLoanService.borrowCoins(
                    request.getEntityId(),
                    request.getAmount(),
                    request.getPurpose()
            );
            response.put("success", true);
            response.put("message", message);
            response.put("entityId", request.getEntityId());
            response.put("amount", request.getAmount());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 环境实体还币
     * 
     * @param request 还币请求
     * @return 操作结果
     */
    @PostMapping("/repay")
    public ResponseEntity<Map<String, Object>> repayCoins(@RequestBody RepayRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String message = ecoCoinLoanService.repayCoins(
                    request.getEntityId(),
                    request.getAmount(),
                    request.getReason()
            );
            response.put("success", true);
            response.put("message", message);
            response.put("entityId", request.getEntityId());
            response.put("amount", request.getAmount());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取环境实体的借币余额
     * 
     * @param entityId 环境实体ID
     * @return 借币余额信息
     */
    @GetMapping("/balance/{entityId}")
    public ResponseEntity<Map<String, Object>> getEntityBorrowedBalance(@PathVariable Long entityId) {
        Map<String, Object> response = new HashMap<>();
        double balance = ecoCoinLoanService.getEntityBorrowedBalance(entityId);
        response.put("entityId", entityId);
        response.put("borrowedBalance", balance);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取智能合约资金池状态
     * 
     * @return 智能合约信息
     */
    @GetMapping("/contract-status")
    public ResponseEntity<SmartContract> getContractStatus() {
        SmartContract contract = ecoCoinLoanService.getContractStatus();
        if (contract == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(contract);
    }

    /**
     * 借币请求DTO
     */
    public static class BorrowRequest {
        private Long entityId;
        private double amount;
        private String purpose;

        public Long getEntityId() {
            return entityId;
        }

        public void setEntityId(Long entityId) {
            this.entityId = entityId;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        public String getPurpose() {
            return purpose;
        }

        public void setPurpose(String purpose) {
            this.purpose = purpose;
        }
    }

    /**
     * 还币请求DTO
     */
    public static class RepayRequest {
        private Long entityId;
        private double amount;
        private String reason;

        public Long getEntityId() {
            return entityId;
        }

        public void setEntityId(Long entityId) {
            this.entityId = entityId;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}