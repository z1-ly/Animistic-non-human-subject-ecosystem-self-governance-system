package com.ecovoice.domain;

public enum CreditContractStatus {
    PENDING("待审批"),
    APPROVED("已批准"),
    FUNDED("已募资"),
    IN_REPAIR("修复中"),
    IN_MONITORING("监测验证中"),
    COMPLETED("已完成"),
    DEFAULTED("违约"),
    CANCELLED("已取消");

    private final String label;

    CreditContractStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}