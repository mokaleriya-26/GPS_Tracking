package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trips")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime; // Entry event

    @Column(name = "end_time")
    private LocalDateTime endTime;   // Exit event

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, COMPLETED, CANCELLED

    @Column(name = "start_latitude", precision = 10, scale = 7)
    private BigDecimal startLatitude;

    @Column(name = "start_longitude", precision = 10, scale = 7)
    private BigDecimal startLongitude;

    @Column(name = "end_latitude", precision = 10, scale = 7)
    private BigDecimal endLatitude;

    @Column(name = "end_longitude", precision = 10, scale = 7)
    private BigDecimal endLongitude;

    @Column(name = "distance_km", precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal distanceKm = BigDecimal.ZERO;

    @Column(name = "duration_seconds")
    @Builder.Default
    private Integer durationSeconds = 0;

    @Column(name = "moving_seconds")
    @Builder.Default
    private Integer movingSeconds = 0;

    @Column(name = "idle_seconds")
    @Builder.Default
    private Integer idleSeconds = 0;

    @Column(name = "max_speed_kmph", precision = 8, scale = 2)
    private BigDecimal maxSpeedKmph;

    @Column(name = "min_speed_kmph", precision = 8, scale = 2)
    private BigDecimal minSpeedKmph;

    @Column(name = "average_speed_kmph", precision = 8, scale = 2)
    private BigDecimal averageSpeedKmph;

    @Column(name = "overspeed_events")
    @Builder.Default
    private Integer overspeedEvents = 0;

    @Column(name = "harsh_braking_events")
    @Builder.Default
    private Integer harshBrakingEvents = 0;

    @Column(name = "harsh_acceleration_events")
    @Builder.Default
    private Integer harshAccelerationEvents = 0;

    @Column(name = "night_driving_seconds")
    @Builder.Default
    private Integer nightDrivingSeconds = 0;

    @Column(name = "alert_count")
    @Builder.Default
    private Integer alertCount = 0;

    @Column(name = "primary_road", length = 255)
    private String primaryRoad;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
