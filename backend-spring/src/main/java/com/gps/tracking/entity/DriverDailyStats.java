package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_daily_stats",
       uniqueConstraints = @UniqueConstraint(columnNames = {"driver_id", "stat_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverDailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "distance_km", precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal distanceKm = BigDecimal.ZERO;

    @Column(name = "driving_seconds")
    @Builder.Default
    private Integer drivingSeconds = 0;

    @Column(name = "idle_seconds")
    @Builder.Default
    private Integer idleSeconds = 0;

    @Column(name = "trip_count")
    @Builder.Default
    private Integer tripCount = 0;

    @Column(name = "max_speed_kmph", precision = 8, scale = 2)
    private BigDecimal maxSpeedKmph;

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

    @Column(name = "fatigue_events")
    @Builder.Default
    private Integer fatigueEvents = 0;

    /**
     * Safety score 0.00 to 100.00.
     *
     * Formula (documented in MIGRATION_PLAN.md Section 10.2):
     * safety_score = 100
     *              - (overspeed_events * 5)
     *              - (harsh_braking_events * 4)
     *              - (harsh_acceleration_events * 4)
     *              - (night_driving_seconds / 3600 * 3)
     *              - (fatigue_events * 8)
     *              + (trips_without_incident * 1)
     * Clamped between 0 and 100.
     */
    @Column(name = "safety_score", precision = 5, scale = 2)
    private BigDecimal safetyScore;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
