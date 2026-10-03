package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gps_aqi_sensor_data")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GpsAqiSensorData {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "trip_id")
    private Trip trip;
    @Column(nullable = false) private LocalDateTime timestamp;
    @Column(nullable = false, precision = 10, scale = 7) private BigDecimal latitude;
    @Column(nullable = false, precision = 10, scale = 7) private BigDecimal longitude;
    @Column(precision = 10, scale = 3) private BigDecimal altitude;
    @Column(name = "pm1_0", precision = 10, scale = 4) private BigDecimal pm1_0;
    @Column(name = "pm2_5", precision = 10, scale = 4) private BigDecimal pm2_5;
    @Column(name = "pm4_0", precision = 10, scale = 4) private BigDecimal pm4_0;
    @Column(name = "pm10", precision = 10, scale = 4) private BigDecimal pm10;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
