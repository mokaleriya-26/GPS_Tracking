package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationStateDTO {
    private String status; // IDLE, RUNNING, PAUSED, COMPLETED, STOPPED
    private String routeId;
    private String routeName;
    private String origin;
    private String destination;

    private int currentWaypointIndex;
    private int totalWaypoints;
    private double progressPercent;

    // Vehicle & Driver info
    private Long vehicleId;
    private String vehicleCode;
    private String vehicleRegistrationNumber;
    private String vehicleType;
    private String makeModel;

    private Long driverId;
    private String driverName;
    private String driverCode;

    // Live Telemetry
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String roadName;
    private BigDecimal currentSpeedKmph;
    private BigDecimal speedLimitKmph;
    private BigDecimal accelerationMps2;

    private BigDecimal distanceTravelledKm;
    private long elapsedSeconds;
    private boolean gpsConnected;

    // Event Trigger Info
    private String currentEvent; // NORMAL, OVERSPEEDING, HARSH_BRAKING, HARSH_ACCELERATION, GPS_DISCONNECT
    private String eventProcessingStatus; // NONE, TRIGGERED, PROCESSING, ALERT_GENERATED, THRESHOLD_NOT_MET
    private String eventMessage;

    // Alerts in this simulation
    @Builder.Default
    private List<AlertDTO> recentAlerts = new ArrayList<>();
    @Builder.Default
    private List<AlertDTO> allAlerts = new ArrayList<>();
    private int totalAlertsCount;
    private BigDecimal totalPenaltyAmount;

    private Long simulationTripId;
    private SimulationSummaryDTO summary;
}
