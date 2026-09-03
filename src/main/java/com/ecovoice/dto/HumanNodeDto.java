package com.ecovoice.dto;

import com.ecovoice.domain.HumanNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HumanNodeDto {

    private Long id;
    private String displayName;
    private String did;
    private String walletAddress;
    private String peerNodeId;
    private double walletBalance;
    private double totalEarned;
    private int tasksCompleted;
    private int tasksClaimed;
    private boolean online;
    private LocalDateTime createdAt;

    public static HumanNodeDto from(HumanNode node) {
        return HumanNodeDto.builder()
                .id(node.getId())
                .displayName(node.getDisplayName())
                .did(node.getDid())
                .walletAddress(node.getWalletAddress())
                .peerNodeId(node.getPeerNodeId())
                .walletBalance(node.getWalletBalance())
                .totalEarned(node.getTotalEarned())
                .tasksCompleted(node.getTasksCompleted())
                .tasksClaimed(node.getTasksClaimed())
                .online(node.isOnline())
                .createdAt(node.getCreatedAt())
                .build();
    }
}
