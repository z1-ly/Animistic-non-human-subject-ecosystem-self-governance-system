package com.ecovoice.domain;

public enum SensorType {
    WATER_PH("水质pH", "pH", 6.5, 8.5),
    SOIL_MOISTURE("土壤湿度", "%", 30, 70),
    AIR_TEMPERATURE("空气温度", "°C", 15, 35),
    AIR_HUMIDITY("空气湿度", "%RH", 40, 80),
    SOUND_DECIBEL("声音分贝", "dB", 35, 75);

    private final String label;
    private final String unit;
    private final double normalMin;
    private final double normalMax;

    SensorType(String label, String unit, double normalMin, double normalMax) {
        this.label = label;
        this.unit = unit;
        this.normalMin = normalMin;
        this.normalMax = normalMax;
    }

    public String getLabel() {
        return label;
    }

    public String getUnit() {
        return unit;
    }

    public double getNormalMin() {
        return normalMin;
    }

    public double getNormalMax() {
        return normalMax;
    }

    public double getDefaultValue() {
        return (normalMin + normalMax) / 2;
    }
}
