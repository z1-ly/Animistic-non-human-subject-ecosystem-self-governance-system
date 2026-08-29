package com.ecovoice.domain;

public enum EcoServiceType {
    WATER_PURIFICATION_SERVICE("水质净化服务"),
    WATER_SUPPLY_SERVICE("清水补给服务"),
    FLOOD_REGULATION_SERVICE("洪水调蓄服务"),
    WIND_BREAK_SERVICE("防风固土服务"),
    POLLINATION_SERVICE("昆虫授粉服务"),
    SOIL_CONSERVATION_SERVICE("水土保持服务"),
    AIR_PURIFICATION_SERVICE("空气净化服务"),
    CARBON_SEQUESTRATION_SERVICE("碳汇服务"),
    BIODIVERSITY_SUPPORT("生物多样性支撑"),
    CLIMATE_REGULATION_SERVICE("气候调节服务");

    private final String label;

    EcoServiceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}