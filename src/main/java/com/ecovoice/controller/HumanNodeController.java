package com.ecovoice.controller;

import com.ecovoice.dto.*;
import com.ecovoice.service.BountyService;
import com.ecovoice.service.HumanNodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class HumanNodeController {

    private final HumanNodeService humanNodeService;
    private final BountyService bountyService;

    @PostMapping("/human-nodes/register")
    public HumanNodeDto register(@Valid @RequestBody HumanNodeRegisterRequest request) {
        return humanNodeService.register(request.getDisplayName());
    }

    @GetMapping("/human-nodes")
    public List<HumanNodeDto> listNodes() {
        return humanNodeService.listNodes();
    }

    @GetMapping("/human-nodes/{id}")
    public HumanNodeDto getNode(@PathVariable Long id) {
        return humanNodeService.getNode(id);
    }

    @GetMapping("/bounties/open")
    public List<BountyTaskDto> openTasks() {
        return bountyService.listOpenTasks();
    }

    @GetMapping("/bounties/active")
    public List<BountyTaskDto> activeTasks() {
        return bountyService.listAllActiveTasks();
    }

    @GetMapping("/human-nodes/{id}/tasks")
    public List<BountyTaskDto> myTasks(@PathVariable Long id) {
        return bountyService.listMyTasks(id);
    }

    @PostMapping("/bounties/{appealId}/claim")
    public NatureAppealDto claim(@PathVariable Long appealId,
                                 @Valid @RequestBody BountyClaimRequest request) {
        return bountyService.claimBounty(appealId, request.getHumanNodeId());
    }
}
