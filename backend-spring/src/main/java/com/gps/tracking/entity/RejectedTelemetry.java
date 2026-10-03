package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "rejected_telemetry")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectedTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imei", length = 20)
    private String imei;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @Column(name = "reason_code", nullable = false, length = 50)
    private String reasonCode; // INVALID_COORDS, MISSING_FIELD, OUT_OF_RANGE, DUPLICATE, etc.

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
