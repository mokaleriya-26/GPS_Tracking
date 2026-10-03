package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code; // e.g. VH001

    @Column(name = "imei", unique = true, length = 20)
    private String imei;

    @Column(name = "registration_number", nullable = false, unique = true, length = 50)
    private String registrationNumber; // e.g. MH04AB1234

    @Column(name = "vehicle_type", length = 50)
    private String vehicleType; // Truck, Van, Car, etc.

    @Column(name = "make_model", length = 100)
    private String makeModel; // e.g. Tata Ace, Maruti Eeco

    @Column(name = "fuel_type", length = 20)
    private String fuelType; // Diesel, Petrol, CNG, Electric

    @Column(name = "tank_capacity_litres", precision = 8, scale = 2)
    private BigDecimal tankCapacityLitres;

    @Column(name = "rated_mileage_kmpl", precision = 8, scale = 2)
    private BigDecimal ratedMileageKmpl;

    @Column(name = "odometer_km", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal odometerKm = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
