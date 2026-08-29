package com.ecovoice.controller;

import com.ecovoice.dto.*;
import com.ecovoice.service.BlockchainLedgerService;
import com.ecovoice.service.BountyService;
import com.ecovoice.service.EntityIdentityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/blockchain")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BlockchainController {

    private final BlockchainLedgerService ledgerService;
    private final EntityIdentityService identityService;
    private final BountyService bountyService;

    @GetMapping("/chain")
    public List<LedgerBlockDto> chain(@RequestParam(defaultValue = "20") int limit) {
        return ledgerService.getChain(limit);
    }

    @GetMapping("/transactions")
    public List<LedgerTransactionDto> transactions(@RequestParam(defaultValue = "30") int limit) {
        return ledgerService.recentTransactions(limit);
    }

    @GetMapping("/entities/{id}/transactions")
    public List<LedgerTransactionDto> entityTransactions(@PathVariable Long id) {
        return ledgerService.entityTransactions(id);
    }

    @GetMapping("/verify")
    public Map<String, Object> verify() {
        return Map.of("valid", ledgerService.verifyChain());
    }

    @GetMapping("/identities")
    public List<EntityIdentityDto> identities() {
        return identityService.listIdentities();
    }

    @GetMapping("/entities/{id}/identity")
    public EntityIdentityDto identity(@PathVariable Long id) {
        return identityService.getIdentity(id);
    }

    @PostMapping("/appeals/{id}/fulfill")
    public NatureAppealDto fulfillBounty(@PathVariable Long id,
                                         @Valid @RequestBody BountyFulfillRequest request) {
        return bountyService.fulfillBounty(id, request);
    }

    @PostMapping("/appeals/{id}/claim")
    public NatureAppealDto claimBounty(@PathVariable Long id,
                                       @Valid @RequestBody BountyClaimRequest request) {
        return bountyService.claimBounty(id, request.getHumanNodeId());
    }
}
