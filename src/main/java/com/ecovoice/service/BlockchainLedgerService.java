package com.ecovoice.service;

import com.ecovoice.domain.*;
import com.ecovoice.dto.LedgerBlockDto;
import com.ecovoice.dto.LedgerTransactionDto;
import com.ecovoice.repository.LedgerBlockRepository;
import com.ecovoice.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 生态币（ECO）分布式账本服务。
 * 每笔交易写入链上区块，同时回写到相应的钱包余额（实体 / 人类节点）。
 */
@Service
@RequiredArgsConstructor
public class BlockchainLedgerService {

    private static final String GENESIS_PREVIOUS = "0000000000000000000000000000000000000000";
    public static final String TOKEN_NAME = "生态币";
    public static final String TOKEN_SYMBOL = "ECO";

    private final LedgerBlockRepository blockRepository;
    private final LedgerTransactionRepository transactionRepository;

    @Transactional
    public void initGenesisIfNeeded() {
        if (blockRepository.count() > 0) return;
        String blockHash = hash("genesis:" + LocalDateTime.now());
        LedgerBlock genesis = blockRepository.save(LedgerBlock.builder()
                .blockIndex(0)
                .previousHash(GENESIS_PREVIOUS)
                .hash(blockHash)
                .build());
        transactionRepository.save(LedgerTransaction.builder()
                .blockId(genesis.getId())
                .txHash(hash("genesis-tx"))
                .type(LedgerTransactionType.GENESIS)
                .fromAddress("0x0000000000000000000000000000000000000000")
                .toAddress("0x0000000000000000000000000000000000000000")
                .amount(0)
                .payload("生态币分布式账本创世块 · " + TOKEN_NAME + "(" + TOKEN_SYMBOL + ")")
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public LedgerTransaction appendTransaction(
            LedgerTransactionType type,
            Long entityId,
            Long appealId,
            String fromAddress,
            String toAddress,
            double amount,
            String payload) {
        LedgerBlock block = mineBlock(resolvePreviousHash(), List.of());
        LedgerTransaction tx = transactionRepository.save(LedgerTransaction.builder()
                .blockId(block.getId())
                .txHash(hash("tx:" + UUID.randomUUID() + ":" + System.nanoTime()))
                .type(type)
                .entityId(entityId)
                .appealId(appealId)
                .fromAddress(fromAddress)
                .toAddress(toAddress)
                .amount(amount)
                .payload(payload)
                .createdAt(LocalDateTime.now())
                .build());
        return tx;
    }

    private LedgerBlock mineBlock(String previousHash, List<String> txHashes) {
        long index = blockRepository.findTopByOrderByBlockIndexDesc()
                .map(b -> b.getBlockIndex() + 1)
                .orElse(0L);
        String content = index + previousHash + txHashes + LocalDateTime.now();
        String blockHash = hash(content);
        return blockRepository.save(LedgerBlock.builder()
                .blockIndex(index)
                .previousHash(previousHash)
                .hash(blockHash)
                .minedAt(LocalDateTime.now())
                .build());
    }

    private String resolvePreviousHash() {
        return blockRepository.findTopByOrderByBlockIndexDesc()
                .map(LedgerBlock::getHash)
                .orElse(GENESIS_PREVIOUS);
    }

    public List<LedgerBlockDto> getChain(int limit) {
        return blockRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(b.getBlockIndex(), a.getBlockIndex()))
                .limit(limit)
                .map(block -> {
                    List<LedgerTransaction> txs = transactionRepository.findAll().stream()
                            .filter(t -> t.getBlockId().equals(block.getId()))
                            .toList();
                    return LedgerBlockDto.from(block, txs);
                })
                .toList();
    }

    public List<LedgerTransactionDto> recentTransactions(int limit) {
        return transactionRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .limit(limit)
                .map(LedgerTransactionDto::from)
                .toList();
    }

    public List<LedgerTransactionDto> entityTransactions(Long entityId) {
        return transactionRepository.findByEntityIdOrderByCreatedAtDesc(entityId).stream()
                .map(LedgerTransactionDto::from)
                .toList();
    }

    public boolean verifyChain() {
        List<LedgerBlock> blocks = blockRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getBlockIndex(), b.getBlockIndex()))
                .toList();
        if (blocks.isEmpty()) return false;
        for (int i = 1; i < blocks.size(); i++) {
            if (!blocks.get(i).getPreviousHash().equals(blocks.get(i - 1).getHash())) {
                return false;
            }
        }
        return true;
    }

    public String generateWalletAddress() {
        return "0x" + hash("wallet:" + UUID.randomUUID()).substring(0, 40);
    }

    public String generateContractAddress(Long appealId) {
        return "0x" + hash("contract:appeal:" + appealId + ":" + System.nanoTime()).substring(0, 40);
    }

    public String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 暴露给同包下辅助服务使用，避免使用反射。 */
    public LedgerTransactionRepository transactionRepository() {
        return transactionRepository;
    }
}
