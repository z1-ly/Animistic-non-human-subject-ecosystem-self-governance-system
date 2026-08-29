package com.ecovoice.domain;

public enum EcoTokenType {
    CARBON_SINK("碳汇生态币", "吨CO₂等价"),
    WATER_PURIFICATION("水质净化权益币", "吨污染物当量"),
    WATER_CONSERVATION("水源涵养权益币", "立方米"),
    BIODIVERSITY("生物多样性权益币", "生物多样性信用单位"),
    CLIMATE_REGULATION("气候调节权益币", "调节服务单位"),
    SOIL_CONSERVATION("水土保持权益币", "吨泥沙拦截量"),
    AIR_PURIFICATION("空气净化权益币", "吨污染物当量"),
    OXYGEN_RELEASE("释氧权益币", "吨O₂等价"),
    POLLINATION("授粉服务权益币", "授粉服务hectare·年"),
    FLOOD_BUFFER("洪水调蓄权益币", "立方米调蓄容量");

    private final String label;
    private final String unit;

    EcoTokenType(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String getLabel() {
        return label;
    }

    public String getUnit() {
        return unit;
    }
}