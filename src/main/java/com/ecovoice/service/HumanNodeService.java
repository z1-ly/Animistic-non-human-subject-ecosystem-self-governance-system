package com.ecovoice.service;

import com.ecovoice.domain.HumanNode;
import com.ecovoice.domain.LedgerTransactionType;
import com.ecovoice.dto.HumanNodeDto;
import com.ecovoice.repository.HumanNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HumanNodeService {

    private final HumanNodeRepository humanNodeRepository;
    private final BlockchainLedgerService ledgerService;

    @Transactional
    public HumanNodeDto register(String displayName) {
        String trimmed = displayName.trim();
        String slug = trimmed.replaceAll("[\\s\\p{Punct}]+", "").toLowerCase();
        if (slug.isEmpty()) slug = "human";
        if (slug.length() > 16) slug = slug.substring(0, 16);
        long seq = humanNodeRepository.count() + 1;
        String peerNodeId = "NODE-H-" + String.format("%06d", seq);
        String wallet = ledgerService.generateWalletAddress();
        String did = "did:ecovoice:human:" + seq + ":" + slug + "-" + (System.currentTimeMillis() % 100000);

        HumanNode node = humanNodeRepository.save(HumanNode.builder()
                .displayName(trimmed)
                .did(did)
                .walletAddress(wallet)
                .peerNodeId(peerNodeId)
                .walletBalance(0)
                .totalEarned(0)
                .tasksCompleted(0)
                .tasksClaimed(0)
                .online(true)
                .build());

        ledgerService.appendTransaction(
                LedgerTransactionType.NODE_JOIN,
                null, null,
                "0x0000000000000000000000000000000000000000",
                wallet, 0,
                "人类节点入网 · " + displayName + " · 生态币钱包 " + peerNodeId);
        return HumanNodeDto.from(node);
    }

    public HumanNodeDto getNode(Long id) {
        return HumanNodeDto.from(humanNodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("人类节点不存在")));
    }

    public HumanNode getNodeEntity(Long id) {
        return humanNodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("人类节点不存在"));
    }

    public List<HumanNodeDto> listNodes() {
        return humanNodeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(HumanNodeDto::from)
                .toList();
    }

    @Transactional
    public void creditPayout(HumanNode node, double amount) {
        node.setWalletBalance(node.getWalletBalance() + amount);
        node.setTotalEarned(node.getTotalEarned() + amount);
        node.setTasksCompleted(node.getTasksCompleted() + 1);
        humanNodeRepository.save(node);
    }

    @Transactional
    public void recordClaim(HumanNode node) {
        node.setTasksClaimed(node.getTasksClaimed() + 1);
        humanNodeRepository.save(node);
    }
}
