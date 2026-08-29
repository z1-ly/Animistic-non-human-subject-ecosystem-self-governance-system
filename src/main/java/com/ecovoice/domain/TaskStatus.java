package com.ecovoice.domain;

public enum TaskStatus {
    PENDING("待开始"),
    IN_PROGRESS("进行中"),
    SUBMITTED("已提交核验"),
    VERIFIED("已核验通过"),
    REJECTED("核验未通过"),
    COMPLETED("已完成");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}