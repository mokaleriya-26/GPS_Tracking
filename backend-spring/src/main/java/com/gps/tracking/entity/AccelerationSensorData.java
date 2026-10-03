package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "acceleration_sensor_data")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AccelerationSensorData {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    /** Timestamp from ADXL335 sensor (1 Hz sampling rate) */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /** X-axis acceleration in g (up to 3 decimal places) */
    @Column(name = "x", nullable = false, precision = 8, scale = 3)
    private BigDecimal x;

    /** Y-axis acceleration in g (up to 3 decimal places) */
    @Column(name = "y", nullable = false, precision = 8, scale = 3)
    private BigDecimal y;

    /** Z-axis acceleration in g (up to 3 decimal places) */
    @Column(name = "z", nullable = false, precision = 8, scale = 3)
    private BigDecimal z;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
