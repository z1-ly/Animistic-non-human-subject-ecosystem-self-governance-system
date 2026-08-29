package com.ecovoice.controller;

import com.ecovoice.dto.*;
import com.ecovoice.service.EcoCoinService;
import com.ecovoice.service.EcologicalValueCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EcoCoinController {

    private final EcoCoinService ecoCoinService;
    private final EcologicalValueCatalogService valueCatalogService;

    @GetMapping("/ecosphere")
    public EcosphereDto ecosphere() {
        return ecoCoinService.ecosphere();
    }

    @GetMapping("/ecosphere/redeem-catalog")
    public List<EcologicalValueDto> redeemCatalog() {
        return valueCatalogService.listAll();
    }

    @GetMapping("/ecosphere/value-catalog")
    public List<EcologicalValueDto> valueCatalog() {
        return valueCatalogService.listAll();
    }

    @GetMapping("/ecosphere/value-catalog/grouped")
    public Map<String, List<EcologicalValueDto>> valueCatalogGrouped() {
        return valueCatalogService.listByMajorCategory();
    }

    @GetMapping("/ecosphere/value-catalog/{key}")
    public EcologicalValueDto valueCatalogItem(@PathVariable String key) {
        return valueCatalogService.getByKey(key);
    }

    // 简化版：不再需要创世初始化和生态增量增发
    // @PostMapping("/ecosphere/genesis")
    // public Map<String, Object> genesis() { ... }

    // @PostMapping("/entities/{id}/regenerate")
    // public Map<String, Object> regenerate(...) { ... }

    @PostMapping("/ecosphere/spend")
    public Map<String, Object> spend(@Valid @RequestBody EcologicalSpendRequest req) {
        return ecoCoinService.spendEcologicalCredit(req);
    }

    @PostMapping("/ecosphere/consume")
    public Map<String, Object> consume(@RequestBody Map<String, Object> payload) {
        Long humanNodeId = ((Number) payload.get("humanNodeId")).longValue();
        Long targetEntityId = ((Number) payload.get("targetEntityId")).longValue();
        String valueTypeKey = (String) payload.get("valueTypeKey");
        if (valueTypeKey == null) {
            valueTypeKey = (String) payload.get("serviceKey");
        }
        Double quantity = payload.get("quantity") != null
                ? ((Number) payload.get("quantity")).doubleValue() : null;
        Double customAmount = payload.get("amount") != null
                ? ((Number) payload.get("amount")).doubleValue() : null;
        double amount = valueCatalogService.resolveRedeemAmount(valueTypeKey, quantity, customAmount);
        String note = (String) payload.getOrDefault("note", "");
        return ecoCoinService.consumePermission(humanNodeId, targetEntityId, valueTypeKey, amount, note);
    }

    @PostMapping("/ecosphere/transfer")
    public Map<String, Object> transfer(@Valid @RequestBody EcoCoinTransferRequest req) {
        return ecoCoinService.transferBetweenEntities(req);
    }

    @GetMapping("/ecosphere/services")
    public List<Map<String, Object>> services() {
        return valueCatalogService.listAll().stream()
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("valueTypeKey", v.getValueTypeKey());
                    m.put("majorCategory", v.getMajorCategory());
                    m.put("coreValueIndicator", v.getCoreValueIndicator());
                    m.put("unit", v.getUnit());
                    m.put("ecoPricePerUnit", v.getEcoPricePerUnit());
                    m.put("defaultQuantity", v.getDefaultQuantity());
                    m.put("defaultTotalEco", v.getDefaultTotalEco());
                    return m;
                })
                .toList();
    }

    // ========== 刚性回流机制相关接口 ==========

    @PostMapping("/ecosphere/consume-with-reflux")
    public Map<String, Object> consumeWithMandatoryReflux(@RequestBody Map<String, Object> payload) {
        Long humanNodeId = ((Number) payload.get("humanNodeId")).longValue();
        Long targetEntityId = ((Number) payload.get("targetEntityId")).longValue();
        String tokenType = (String) payload.get("tokenType");
        Double amount = ((Number) payload.get("amount")).doubleValue();
        String consumptionReason = (String) payload.getOrDefault("consumptionReason", "生态资源消耗");

        return ecoCoinService.consumeEcoTokenWithMandatoryReflux(
                humanNodeId, targetEntityId, tokenType, amount, consumptionReason);
    }

    @PostMapping("/ecosphere/penalize")
    public Map<String, Object> penalizeOverConsumption(@RequestBody Map<String, Object> payload) {
        Long humanNodeId = ((Number) payload.get("humanNodeId")).longValue();
        Long targetEntityId = ((Number) payload.get("targetEntityId")).longValue();
        String violationType = (String) payload.get("violationType");
        Double baseAmount = ((Number) payload.get("baseAmount")).doubleValue();
        String violationDetails = (String) payload.getOrDefault("violationDetails", "生态违规行为");

        return ecoCoinService.penalizeOverConsumption(
                humanNodeId, targetEntityId, violationType, baseAmount, violationDetails);
    }
}
