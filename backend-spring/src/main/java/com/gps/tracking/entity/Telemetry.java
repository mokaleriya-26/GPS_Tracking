package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "telemetry")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "speed_kmph", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal speedKmph = BigDecimal.ZERO;

    @Column(name = "heading", precision = 6, scale = 2)
    private BigDecimal heading;

    @Column(name = "ignition")
    @Builder.Default
    private Boolean ignition = false;

    @Column(name = "battery_voltage", precision = 6, scale = 3)
    private BigDecimal batteryVoltage;

    @Column(name = "gsm_signal")
    private Integer gsmSignal;

    @Column(name = "satellites")
    private Integer satellites;

    @Column(name = "distance_from_previous_km", precision = 10, scale = 4)
    private BigDecimal distanceFromPreviousKm;

    @Column(name = "seconds_from_previous")
    private Integer secondsFromPrevious;

    @Column(name = "acceleration_mps2", precision = 8, scale = 4)
    private BigDecimal accelerationMps2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(name = "road_status", length = 20)
    private String roadStatus; // ON_ROAD, OFF_ROAD, STATIONARY

    @Column(name = "road_name", length = 255)
    private String roadName;

    @Column(name = "road_ref", length = 100)
    private String roadRef;

    @Column(name = "highway_type", length = 50)
    private String highwayType;

    @Column(name = "osm_way_id")
    private Long osmWayId;

    @Column(name = "road_distance_m", precision = 10, scale = 2)
    private BigDecimal roadDistanceM;

    @Column(name = "speed_limit_kmph", precision = 8, scale = 2)
    private BigDecimal speedLimitKmph;

    @Column(name = "speed_limit_source", length = 50)
    private String speedLimitSource;

    @Column(name = "speed_limit_inferred")
    @Builder.Default
    private Boolean speedLimitInferred = false;

    @Column(name = "source", length = 50)
    @Builder.Default
    private String source = "GPS";

    @PrePersist
    public void prePersist() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
