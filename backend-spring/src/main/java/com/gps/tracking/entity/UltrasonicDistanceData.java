package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ultrasonic_distance_data")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UltrasonicDistanceData {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "trip_id")
    private Trip trip;
    @Column(nullable = false) private LocalDateTime timestamp;
    @Column(name = "distance_cm", nullable = false, precision = 10, scale = 3) private BigDecimal distanceCm;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
