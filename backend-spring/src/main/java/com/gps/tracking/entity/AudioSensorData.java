package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audio_sensor_data")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AudioSensorData {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "trip_id")
    private Trip trip;
    @Column(nullable = false) private LocalDateTime timestamp;
    @Column(name = "audio_sample_pcm", columnDefinition = "TEXT") private String audioSamplePcm;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
