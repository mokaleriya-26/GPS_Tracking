package com.gps.tracking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Deterministic unique event reference.
     * Maps to 'event_id' from old Django system (poll_csv.py).
     */
    @Column(name = "reference", unique = true, length = 200)
    private String reference;

    /**
     * Alert types (preserved from old system plus new types):
     * - Overspeed Alert
     * - Harsh Braking Alert
     * - GPS Disconnect Alert
     * - Night Driving Alert
     * - Ignition Alert
     * - Harsh Acceleration Alert
     * - Fatigue Alert
     * - Geofence Alert
     */
    @Column(name = "alert_type", nullable = false, length = 100)
    private String alertType;

    /**
     * Severity: INFO, WARNING, CRITICAL
     */
    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private String severity = "WARNING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "speed_kmph", precision = 8, scale = 2)
    private BigDecimal speedKmph;

    @Column(name = "speed_limit_kmph", precision = 8, scale = 2)
    private BigDecimal speedLimitKmph;

    @Column(name = "speed_limit_source", length = 50)
    private String speedLimitSource;

    @Column(name = "speed_limit_inferred")
    @Builder.Default
    private Boolean speedLimitInferred = false;

    @Column(name = "road_name", length = 255)
    private String roadName;

    @Column(name = "previous_speed_kmph", precision = 8, scale = 2)
    private BigDecimal previousSpeedKmph;

    @Column(name = "seconds_between")
    private Integer secondsBetween;

    @Column(name = "acceleration_mps2", precision = 8, scale = 4)
    private BigDecimal accelerationMps2;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    // Trip context (preserved from old NotificationLog / Alert model)
    @Column(name = "trip_start_time")
    private LocalDateTime tripStartTime;

    @Column(name = "trip_end_time")
    private LocalDateTime tripEndTime;

    @Column(name = "trip_distance_km", precision = 10, scale = 3)
    private BigDecimal tripDistanceKm;

    @Column(name = "trip_duration_min")
    private Integer tripDurationMin;

    @Column(name = "min_speed_kmph", precision = 8, scale = 2)
    private BigDecimal minSpeedKmph;

    @Column(name = "max_speed_kmph", precision = 8, scale = 2)
    private BigDecimal maxSpeedKmph;

    // Alert lifecycle
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN"; // OPEN, RESOLVED, HISTORICAL

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    // Notification delivery tracking
    @Column(name = "delivery_status", length = 20)
    @Builder.Default
    private String deliveryStatus = "PENDING";

    @Column(name = "delivery_attempts")
    @Builder.Default
    private Integer deliveryAttempts = 0;

    @Column(name = "delivery_last_error", columnDefinition = "TEXT")
    private String deliveryLastError;

    @Column(name = "delivery_next_attempt_at")
    private LocalDateTime deliveryNextAttemptAt;

    // Email delivery status (per-recipient, from old NotificationLog)
    @Column(name = "email_sent")
    @Builder.Default
    private Boolean emailSent = false;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "email_error", columnDefinition = "TEXT")
    private String emailError;

    @Column(name = "fleet_email_sent")
    @Builder.Default
    private Boolean fleetEmailSent = false;

    @Column(name = "fleet_email_error", columnDefinition = "TEXT")
    private String fleetEmailError;

    @Column(name = "manager_email_sent")
    @Builder.Default
    private Boolean managerEmailSent = false;

    @Column(name = "manager_email_error", columnDefinition = "TEXT")
    private String managerEmailError;

    @Column(name = "driver_email_sent")
    @Builder.Default
    private Boolean driverEmailSent = false;

    @Column(name = "driver_email_error", columnDefinition = "TEXT")
    private String driverEmailError;

    // SMS delivery status (per-recipient, from old NotificationLog)
    @Column(name = "sms_sent")
    @Builder.Default
    private Boolean smsSent = false;

    @Column(name = "sms_sent_at")
    private LocalDateTime smsSentAt;

    @Column(name = "sms_error", columnDefinition = "TEXT")
    private String smsError;

    @Column(name = "fleet_sms_sent")
    @Builder.Default
    private Boolean fleetSmsSent = false;

    @Column(name = "fleet_sms_error", columnDefinition = "TEXT")
    private String fleetSmsError;

    @Column(name = "fleet_sms_request_id", length = 100)
    private String fleetSmsRequestId;

    @Column(name = "manager_sms_sent")
    @Builder.Default
    private Boolean managerSmsSent = false;

    @Column(name = "manager_sms_error", columnDefinition = "TEXT")
    private String managerSmsError;

    @Column(name = "manager_sms_request_id", length = 100)
    private String managerSmsRequestId;

    @Column(name = "driver_sms_sent")
    @Builder.Default
    private Boolean driverSmsSent = false;

    @Column(name = "driver_sms_error", columnDefinition = "TEXT")
    private String driverSmsError;

    @Column(name = "driver_sms_request_id", length = 100)
    private String driverSmsRequestId;

    // WhatsApp (placeholder)
    @Column(name = "whatsapp_sent")
    @Builder.Default
    private Boolean whatsappSent = false;

    @Column(name = "whatsapp_error", columnDefinition = "TEXT")
    private String whatsappError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
