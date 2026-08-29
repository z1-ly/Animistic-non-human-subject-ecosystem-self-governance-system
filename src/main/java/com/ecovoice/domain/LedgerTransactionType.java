package com.ecovoice.domain;

public enum LedgerTransactionType {
    GENESIS("创世分配"),
    BOUNTY_ESCROW("悬赏托管"),
    BOUNTY_PAYOUT("生态治理奖励"),
    DATA_ROYALTY("数据收益"),
    ECO_REGENERATION("生态增量增发"),
    ECOLOGICAL_FEE("生态资源消耗费"),
    ECO_DESTRUCTION_FINE("破坏加倍扣除"),
    ENTITY_TRANSFER("跨自然体结算"),
    CONTRACT_DEPLOY("合约部署"),
    NODE_JOIN("节点入网"),
    BOUNTY_CLAIM("任务接取"),
    BOUNTY_FULFILL("任务提交评估"),
    DAO_VOTE_FEE("DAO投票权服务费"),
    VISITOR_PERMIT_FEE("观研入园许可"),
    LOAN("借币"),
    REPAY("还币");

    private final String label;

    LedgerTransactionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
