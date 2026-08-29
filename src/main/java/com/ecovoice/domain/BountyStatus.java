package com.ecovoice.domain;

public enum BountyStatus {
    OPEN("悬赏中"),
    CLAIMED("已接取"),
    FULFILLED("已提交"),
    PAID("已支付"),
    SETTLED("已结清"),
    EXPIRED("已过期"),
    CANCELLED("已撤销");

    private final String label;

    BountyStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
