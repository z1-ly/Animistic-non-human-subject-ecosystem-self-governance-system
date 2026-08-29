package com.ecovoice.domain;

public enum SettlementStatus {
    PENDING("待清算"),
    CALCULATING("计算中"),
    COMPLETED("已完成"),
    FAILED("清算失败");

    private final String label;

    SettlementStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}