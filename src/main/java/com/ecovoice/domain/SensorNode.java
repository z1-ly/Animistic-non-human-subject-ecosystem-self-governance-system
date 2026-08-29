package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sensor_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 60)
    private String nodeCode;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SensorType sensorType;

    @Column(length = 120)
    private String position;

    private boolean online;

    @Column(name = "`last_value`")
    private Double lastValue;
}
