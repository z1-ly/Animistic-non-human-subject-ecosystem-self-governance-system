package com.ecovoice.domain;

public enum RestorationTaskType {
    ALGAE_REMOVAL("除藻净水"),
    VEGETATION_RESTORATION("水生植被重构"),
    OXYGENATION("生态增氧"),
    BIOLOGICAL_RESTORATION("生物群落修复"),
    SOIL_IMPROVEMENT("土壤改良"),
    WATER_QUALITY_MONITORING("水质监测"),
    BIODIVERSITY_MONITORING("生物多样性监测");

    private final String label;

    RestorationTaskType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}