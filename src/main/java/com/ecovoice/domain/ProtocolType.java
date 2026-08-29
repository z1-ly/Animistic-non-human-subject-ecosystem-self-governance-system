package com.ecovoice.domain;

public enum ProtocolType {
    MQTT("MQTT", "轻量发布/订阅，适合低功耗节点"),
    HTTP_REST("HTTP/REST", "RESTful 直传，易于调试"),
    WEBSOCKET("WebSocket", "全双工实时通道"),
    COAP("CoAP", "受限设备 UDP 协议"),
    MODBUS_RTU("Modbus RTU", "工业现场总线");

    private final String label;
    private final String description;

    ProtocolType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
