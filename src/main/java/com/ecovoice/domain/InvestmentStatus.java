package com.ecovoice.domain;

public enum InvestmentStatus {
    PENDING("待确认"),
    CONFIRMED("已确认"),
    LOCKED("已锁定"),
    SETTLED("已清算"),
    FAILED("清算失败");

    private final String label;

    InvestmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}