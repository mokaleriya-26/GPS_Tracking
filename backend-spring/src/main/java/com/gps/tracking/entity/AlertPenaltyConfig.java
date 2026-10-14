package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "alert_penalty_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertPenaltyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_type", nullable = false, unique = true, length = 100)
    private String alertType;

    @Column(name = "weight", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal weight = BigDecimal.valueOf(5.0);

    @Column(name = "default_fine_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal defaultFineAmount = BigDecimal.ZERO;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "updated_by", length = 100)
    @Builder.Default
    private String updatedBy = "SYSTEM";

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
