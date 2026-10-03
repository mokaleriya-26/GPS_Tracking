package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertDTO {
    private Long id;
    private String reference;
    private String alertType;
    private String severity;
    private Long vehicleId;
    private String vehicleCode;
    private String vehicleRegistrationNumber;
    private Long driverId;
    private String driverName;
    private String driverCode;
    private String driverPhone;
    private Long tripId;
    private LocalDateTime occurredAt;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal speedKmph;
    private BigDecimal speedLimitKmph;
    private String roadName;
    private BigDecimal accelerationMps2;
    private String message;
    private String status;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    // Trip context (from old system)
    private LocalDateTime tripStartTime;
    private LocalDateTime tripEndTime;
    private BigDecimal tripDistanceKm;
    private Integer tripDurationMin;
    private BigDecimal minSpeedKmph;
    private BigDecimal maxSpeedKmph;
    // Notification delivery
    private String deliveryStatus;
    private Integer deliveryAttempts;
    private Boolean emailSent;
    private Boolean fleetEmailSent;
    private Boolean managerEmailSent;
    private Boolean driverEmailSent;
    private String emailError;
    private Boolean smsSent;
    private Boolean fleetSmsSent;
    private String fleetSmsRequestId;
    private Boolean managerSmsSent;
    private String managerSmsRequestId;
    private Boolean driverSmsSent;
    private String driverSmsRequestId;
    private String smsError;
    private Boolean isSilenced; // true when status=HISTORICAL (maps to old is_silenced)
    private LocalDateTime createdAt;
}
