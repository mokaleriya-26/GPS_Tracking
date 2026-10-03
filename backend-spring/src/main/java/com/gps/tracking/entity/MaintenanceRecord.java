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
@Table(name = "maintenance_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "service_type", nullable = false, length = 100)
    private String serviceType; // OIL_CHANGE, TIRE_ROTATION, BRAKE_SERVICE, ENGINE_TUNE, etc.

    @Column(name = "serviced_on", nullable = false)
    private LocalDate servicedOn;

    @Column(name = "odometer_km", precision = 10, scale = 2)
    private BigDecimal odometerKm;

    @Column(name = "cost_inr", precision = 12, scale = 2)
    private BigDecimal costInr;

    @Column(name = "next_service_due_on")
    private LocalDate nextServiceDueOn;

    @Column(name = "next_service_due_km", precision = 10, scale = 2)
    private BigDecimal nextServiceDueKm;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "data_source", length = 50)
    @Builder.Default
    private String dataSource = "MANUAL";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
