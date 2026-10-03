package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "camera_sensor_data")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CameraSensorData {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "trip_id")
    private Trip trip;
    @Column(nullable = false) private LocalDateTime timestamp;
    @Column(name = "frame_no", nullable = false) private Long frameNo;
    @Column(length = 20) @Builder.Default private String resolution = "1920x1080";
    @Column(precision = 6, scale = 2) @Builder.Default private BigDecimal fps = new BigDecimal("1.0");
    @Column(name = "video_file", length = 500) private String videoFile;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
