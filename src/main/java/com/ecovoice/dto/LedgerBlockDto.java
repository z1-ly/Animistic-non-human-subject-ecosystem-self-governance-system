package com.ecovoice.dto;

import com.ecovoice.domain.LedgerBlock;
import com.ecovoice.domain.LedgerTransaction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class LedgerBlockDto {

    private long blockIndex;
    private String previousHash;
    private String hash;
    private LocalDateTime minedAt;
    private String miner;
    private List<LedgerTransactionDto> transactions;

    public static LedgerBlockDto from(LedgerBlock block, List<LedgerTransaction> txs) {
        return LedgerBlockDto.builder()
                .blockIndex(block.getBlockIndex())
                .previousHash(block.getPreviousHash())
                .hash(block.getHash())
                .minedAt(block.getMinedAt())
                .miner(block.getMiner())
                .transactions(txs.stream().map(LedgerTransactionDto::from).toList())
                .build();
    }
}
