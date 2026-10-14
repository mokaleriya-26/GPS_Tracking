package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_penalties")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverPenalty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    /**
     * Penalty types:
     * - MANUAL_SAFETY_DEDUCTION (explicitly deducts points from safety score)
     * - FINANCIAL_FINE (monetary fine only, not safety score)
     * - POLICY_VIOLATION
     * - WARNING
     */
    @Column(name = "penalty_type", nullable = false, length = 50)
    private String penaltyType;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "penalty_points", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal penaltyPoints = BigDecimal.ZERO;

    @Column(name = "monetary_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal monetaryAmount = BigDecimal.ZERO;

    @Column(name = "date_applied", nullable = false)
    @Builder.Default
    private LocalDateTime dateApplied = LocalDateTime.now();

    @Column(name = "applied_by", nullable = false, length = 100)
    @Builder.Default
    private String appliedBy = "Fleet Manager";

    /**
     * Status: ACTIVE, REVOKED, WAIVED
     */
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * Flag whether this penalty deducts from the safety score.
     * Prevents duplicate deduction if the underlying alert is already in the formula.
     */
    @Column(name = "deduct_from_score", nullable = false)
    @Builder.Default
    private Boolean deductFromScore = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
