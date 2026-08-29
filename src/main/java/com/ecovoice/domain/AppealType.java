package com.ecovoice.domain;

public enum AppealType {
    THIRST("口渴补水"),
    QUIET_REQUEST("安静环境"),
    WATER_QUALITY("水质调节"),
    HEAT_STRESS("降温遮阳"),
    COLD_STRESS("保温防寒"),
    DRY_AIR("空气加湿"),
    NOISE_DAYTIME("日间噪声");

    private final String label;

    AppealType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
