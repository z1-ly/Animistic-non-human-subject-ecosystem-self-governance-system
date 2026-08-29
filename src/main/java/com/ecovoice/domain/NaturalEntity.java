package com.ecovoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "natural_entities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NaturalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 40)
    private String category;

    @Column(length = 200)
    private String location;

    @Column(length = 500)
    private String description;

    @Column(length = 20)
    private String status;

    @Column(name = "avatar_color", length = 16)
    private String avatarColor;

    @Column(name = "borrowed_balance", nullable = false)
    private double borrowedBalance;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
        borrowedBalance = 0.0;
    }
}
