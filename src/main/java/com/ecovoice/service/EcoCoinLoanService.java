package com.ecovoice.service;

import com.ecovoice.domain.LedgerTransactionType;
import com.ecovoice.domain.NaturalEntity;
import com.ecovoice.domain.SmartContract;
import com.ecovoice.domain.EntityIdentity;
import com.ecovoice.repository.NaturalEntityRepository;
import com.ecovoice.repository.SmartContractRepository;
import com.ecovoice.repository.EntityIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生态币借贷服务
 * 管理环境实体从智能合约借币和还币的逻辑
 */
@Service
@RequiredArgsConstructor
public class EcoCoinLoanService {

    private final SmartContractRepository smartContractRepository;
    private final NaturalEntityRepository naturalEntityRepository;
    private final EntityIdentityRepository entityIdentityRepository;
    private final BlockchainLedgerService blockchainLedgerService;

    /**
     * 环境实体从智能合约借币
     * 
     * @param entityId 环境实体ID
     * @param amount 借币数量
     * @param purpose 借币用途
     * @return 交易信息
     */
    @Transactional
    public String borrowCoins(Long entityId, double amount, String purpose) {
        if (amount <= 0) {
            throw new IllegalArgumentException("借币金额必须大于0");
        }

        NaturalEntity entity = naturalEntityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("环境实体不存在"));

        SmartContract contract = smartContractRepository.findByContractName("主生态币资金池")
                .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));

        if (contract.getAvailableBalance() < amount) {
            throw new IllegalStateException("智能合约可用余额不足，无法借币");
        }

        EntityIdentity identity = entityIdentityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalStateException("主体钱包未初始化"));

        contract.lend(amount);
        smartContractRepository.save(contract);

        entity.setBorrowedBalance(entity.getBorrowedBalance() + amount);
        naturalEntityRepository.save(entity);

        identity.setWalletBalance(identity.getWalletBalance() + amount);
        entityIdentityRepository.save(identity);

        blockchainLedgerService.appendTransaction(
                LedgerTransactionType.LOAN,
                entityId,
                null,
                contract.getContractAddress(),
                identity.getWalletAddress(),
                amount,
                purpose != null ? purpose : "环境实体借币用于生态治理"
        );

        return "借币成功，借入 " + amount + " 生态币";
    }

    /**
     * 环境实体归还生态币给智能合约
     * 
     * @param entityId 环境实体ID
     * @param amount 还币数量
     * @param reason 还币原因
     * @return 交易信息
     */
    @Transactional
    public String repayCoins(Long entityId, double amount, String reason) {
        if (amount <= 0) {
            throw new IllegalArgumentException("还币金额必须大于0");
        }

        NaturalEntity entity = naturalEntityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("环境实体不存在"));

        if (entity.getBorrowedBalance() < amount) {
            throw new IllegalStateException("还币金额超过已借币余额，当前借币余额: " + entity.getBorrowedBalance());
        }

        SmartContract contract = smartContractRepository.findByContractName("主生态币资金池")
                .orElseThrow(() -> new IllegalStateException("智能合约未初始化"));

        EntityIdentity identity = entityIdentityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalStateException("主体钱包未初始化"));

        contract.recover(amount);
        smartContractRepository.save(contract);

        entity.setBorrowedBalance(entity.getBorrowedBalance() - amount);
        naturalEntityRepository.save(entity);

        identity.setWalletBalance(Math.max(0, identity.getWalletBalance() - amount));
        entityIdentityRepository.save(identity);

        blockchainLedgerService.appendTransaction(
                LedgerTransactionType.REPAY,
                entityId,
                null,
                identity.getWalletAddress(),
                contract.getContractAddress(),
                amount,
                reason != null ? reason : "任务完成归还生态币"
        );

        return "还币成功，归还 " + amount + " 生态币";
    }

    /**
     * 获取环境实体的借币余额
     * 
     * @param entityId 环境实体ID
     * @return 借币余额
     */
    public double getEntityBorrowedBalance(Long entityId) {
        return naturalEntityRepository.findById(entityId)
                .map(NaturalEntity::getBorrowedBalance)
                .orElse(0.0);
    }

    /**
     * 获取智能合约的资金池状态
     * 
     * @return 智能合约信息
     */
    public SmartContract getContractStatus() {
        return smartContractRepository.findAll().stream()
                .findFirst()
                .orElse(null);
    }
}