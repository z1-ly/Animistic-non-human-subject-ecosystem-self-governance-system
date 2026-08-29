package com.ecovoice.domain;

public enum EmotionState {
    PLEASANT("愉悦", "#76ff03"),
    DISCOMFORT("不适", "#ff9100"),
    CRISIS("危机", "#ff5252");

    private final String label;
    private final String color;

    EmotionState(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public static EmotionState fromScore(double score) {
        if (score >= 75) {
            return PLEASANT;
        }
        if (score >= 40) {
            return DISCOMFORT;
        }
        return CRISIS;
    }
}
