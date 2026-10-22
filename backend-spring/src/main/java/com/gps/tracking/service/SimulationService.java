package com.gps.tracking.service;

import com.gps.tracking.dto.*;
import com.gps.tracking.entity.*;
import com.gps.tracking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private final SimulationRouteProvider routeProvider;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final AlertRepository alertRepository;
    private final DriverPenaltyRepository driverPenaltyRepository;
    private final AlertService alertService;
    private final PenaltyService penaltyService;

    // Simulation Session State
    private final Object stateLock = new Object();
    private String status = "IDLE"; // IDLE, RUNNING, PAUSED, COMPLETED, STOPPED
    private SimulationRouteDTO currentRoute;
    private int currentWaypointIndex = 0;
    private int speedMultiplier = 1;

    private Long simulationTripId;
    private Vehicle simulationVehicle;
    private Driver simulationDriver;

    private BigDecimal currentLatitude = new BigDecimal("19.0069");
    private BigDecimal currentLongitude = new BigDecimal("73.1018");
    private String currentRoadName = "JNPT-Kalamboli Access Road";
    private BigDecimal currentSpeedKmph = BigDecimal.ZERO;
    private BigDecimal previousSpeedKmph = BigDecimal.ZERO;
    private BigDecimal currentSpeedLimitKmph = new BigDecimal("60.00");
    private BigDecimal currentAccelerationMps2 = BigDecimal.ZERO;

    private BigDecimal totalDistanceKm = BigDecimal.ZERO;
    private long elapsedSeconds = 0;
    private boolean gpsConnected = true;

    // Manual Event Trigger State
    private String currentEvent = "NORMAL"; // NORMAL, OVERSPEEDING, HARSH_BRAKING, HARSH_ACCELERATION, GPS_DISCONNECT
    private String pendingEvent = null;
    private String eventProcessingStatus = "NONE"; // NONE, TRIGGERED, PROCESSING, ALERT_GENERATED, THRESHOLD_NOT_MET
    private String eventMessage = "Truck operating normally within configured parameters.";

    // Session tracking
    private LocalDateTime simulationStartTime;
    private BigDecimal maxSpeedRecorded = BigDecimal.ZERO;
    private final List<BigDecimal> speedHistory = new CopyOnWriteArrayList<>();
    private final List<AlertDTO> recentAlerts = new CopyOnWriteArrayList<>();
    private final List<AlertDTO> allAlerts = new CopyOnWriteArrayList<>();
    private final Set<String> triggeredAlertKeysInSession = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> processedEventIds = Collections.synchronizedSet(new LinkedHashSet<>());

    private SimulationSummaryDTO finalSummary = null;

    /**
     * Start a new simulation journey.
     */
    @Transactional
    public SimulationStateDTO startSimulation(String routeId, Integer multiplier) {
        synchronized (stateLock) {
            log.info("Starting simulation session on route {} with multiplier {}x", routeId, multiplier);
            currentRoute = routeProvider.getRoute(routeId);
            speedMultiplier = (multiplier != null && multiplier > 0) ? multiplier : 1;
            currentWaypointIndex = 0;
            status = "RUNNING";
            elapsedSeconds = 0;
            totalDistanceKm = BigDecimal.ZERO;
            gpsConnected = true;
            currentEvent = "NORMAL";
            pendingEvent = null;
            eventProcessingStatus = "NONE";
            eventMessage = "Simulation started. Journey underway on " + currentRoute.getName();
            simulationStartTime = LocalDateTime.now();
            speedHistory.clear();
            recentAlerts.clear();
            allAlerts.clear();
            triggeredAlertKeysInSession.clear();
            processedEventIds.clear();
            finalSummary = null;

            // Ensure dedicated simulation vehicle and driver
            simulationVehicle = getOrCreateSimulationVehicle();
            simulationDriver = getOrCreateSimulationDriver();

            SimulationWaypointDTO startWp = currentRoute.getWaypoints().get(0);
            currentLatitude = startWp.getLatitude();
            currentLongitude = startWp.getLongitude();
            currentRoadName = startWp.getRoadName();
            currentSpeedLimitKmph = startWp.getSpeedLimitKmph();
            currentSpeedKmph = startWp.getDefaultSpeedKmph();
            previousSpeedKmph = currentSpeedKmph;
            currentAccelerationMps2 = BigDecimal.ZERO;
            maxSpeedRecorded = currentSpeedKmph;
            speedHistory.add(currentSpeedKmph);

            // Create persistent simulation Trip in PostgreSQL
            Trip trip = Trip.builder()
                    .vehicle(simulationVehicle)
                    .driver(simulationDriver)
                    .startTime(simulationStartTime)
                    .status("ACTIVE")
                    .startLatitude(currentLatitude)
                    .startLongitude(currentLongitude)
                    .distanceKm(BigDecimal.ZERO)
                    .durationSeconds(0)
                    .maxSpeedKmph(currentSpeedKmph)
                    .minSpeedKmph(currentSpeedKmph)
                    .averageSpeedKmph(currentSpeedKmph)
                    .primaryRoad(currentRoadName)
                    .isSimulation(true)
                    .build();
            trip = tripRepository.save(trip);
            simulationTripId = trip.getId();

            return getCurrentState();
        }
    }

    /**
     * Pause the running simulation.
     */
    public SimulationStateDTO pauseSimulation() {
        synchronized (stateLock) {
            if ("RUNNING".equals(status)) {
                status = "PAUSED";
                eventMessage = "Simulation paused at waypoint " + (currentWaypointIndex + 1);
            }
            return getCurrentState();
        }
    }

    /**
     * Resume a paused simulation.
     */
    public SimulationStateDTO resumeSimulation() {
        synchronized (stateLock) {
            if ("PAUSED".equals(status)) {
                status = "RUNNING";
                eventMessage = "Simulation resumed. Moving along route.";
            }
            return getCurrentState();
        }
    }

    /**
     * Stop the simulation.
     */
    @Transactional
    public SimulationStateDTO stopSimulation() {
        synchronized (stateLock) {
            if (!"STOPPED".equals(status) && !"COMPLETED".equals(status) && !"IDLE".equals(status)) {
                status = "STOPPED";
                eventMessage = "Simulation stopped by operator.";
                finalizeTrip("STOPPED");
            }
            return getCurrentState();
        }
    }

    /**
     * Reset the simulation and safely clear only this simulation's records.
     * Safeguard: NEVER touches operational fleet records!
     */
    @Transactional
    public SimulationStateDTO resetSimulation() {
        synchronized (stateLock) {
            log.info("Resetting simulation session and performing safe cleanup of simulation-generated records.");
            try {
                // Safe cleanup: Delete all records with is_simulation = true
                List<Alert> simAlerts = alertRepository.findByIsSimulationTrue();
                for (Alert a : simAlerts) {
                    driverPenaltyRepository.findByAlertId(a.getId())
                            .forEach(driverPenaltyRepository::delete);
                    alertRepository.delete(a);
                }
                if (simulationTripId != null) {
                    tripRepository.findById(simulationTripId).ifPresent(t -> {
                        if (Boolean.TRUE.equals(t.getIsSimulation())) {
                            tripRepository.delete(t);
                        }
                    });
                }
            } catch (Exception ex) {
                log.warn("Non-fatal error during simulation cleanup: {}", ex.getMessage());
            }

            status = "IDLE";
            currentWaypointIndex = 0;
            simulationTripId = null;
            elapsedSeconds = 0;
            totalDistanceKm = BigDecimal.ZERO;
            currentSpeedKmph = BigDecimal.ZERO;
            previousSpeedKmph = BigDecimal.ZERO;
            currentAccelerationMps2 = BigDecimal.ZERO;
            gpsConnected = true;
            currentEvent = "NORMAL";
            pendingEvent = null;
            eventProcessingStatus = "NONE";
            eventMessage = "Simulation reset. Ready to start.";
            recentAlerts.clear();
            allAlerts.clear();
            speedHistory.clear();
            triggeredAlertKeysInSession.clear();
            processedEventIds.clear();
            finalSummary = null;

            if (currentRoute == null) {
                currentRoute = routeProvider.getRoute("mumbai-pune-expressway");
            }
            SimulationWaypointDTO startWp = currentRoute.getWaypoints().get(0);
            currentLatitude = startWp.getLatitude();
            currentLongitude = startWp.getLongitude();
            currentRoadName = startWp.getRoadName();
            currentSpeedLimitKmph = startWp.getSpeedLimitKmph();

            return getCurrentState();
        }
    }

    /**
     * Trigger a deliberate manual simulation event.
     * Every deliberate event action creates a distinct alert occurrence, records it in PostgreSQL,
     * triggers individual email notifications, and computes progressive penalties.
     * Idempotency is enforced using eventId to avoid duplicate processing on network retries.
     */
    @Transactional
    public SimulationStateDTO triggerEvent(String eventType, BigDecimal overrideSpeed, String eventId) {
        synchronized (stateLock) {
            if (!"RUNNING".equals(status)) {
                eventMessage = "Cannot trigger event: simulation is not currently running.";
                return getCurrentState();
            }

            if (eventId != null && !eventId.trim().isEmpty()) {
                String trimmedId = eventId.trim();
                if (processedEventIds.contains(trimmedId)) {
                    log.info("Simulation event {} already processed (eventId: {}). Returning idempotent state.", eventType, trimmedId);
                    return getCurrentState();
                }
                processedEventIds.add(trimmedId);
                if (processedEventIds.size() > 1000) {
                    Iterator<String> it = processedEventIds.iterator();
                    if (it.hasNext()) {
                        it.next();
                        it.remove();
                    }
                }
            }

            String normalized = (eventType != null) ? eventType.toUpperCase().trim() : "NORMAL";
            currentEvent = normalized;
            eventProcessingStatus = "PROCESSING";

            SimulationWaypointDTO wp = (currentRoute != null && !currentRoute.getWaypoints().isEmpty())
                    ? currentRoute.getWaypoints().get(Math.min(currentWaypointIndex, currentRoute.getWaypoints().size() - 1))
                    : new SimulationWaypointDTO();

            String uniqueSuffix = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);

            switch (normalized) {
                case "OVERSPEED":
                case "OVERSPEEDING": {
                    BigDecimal targetSpeed = (overrideSpeed != null && overrideSpeed.compareTo(BigDecimal.ZERO) > 0)
                            ? overrideSpeed
                            : currentSpeedLimitKmph.add(new BigDecimal("25.00"));
                    previousSpeedKmph = currentSpeedKmph;
                    currentSpeedKmph = targetSpeed;
                    currentAccelerationMps2 = computeAcceleration(previousSpeedKmph, currentSpeedKmph, 2);
                    if (currentSpeedKmph.compareTo(maxSpeedRecorded) > 0) {
                        maxSpeedRecorded = currentSpeedKmph;
                    }
                    speedHistory.add(currentSpeedKmph);

                    String msg = String.format("Vehicle exceeded speed limit: %.1f km/h (Limit: %.1f km/h) on %s.",
                            currentSpeedKmph.doubleValue(), currentSpeedLimitKmph.doubleValue(), wp.getRoadName() != null ? wp.getRoadName() : currentRoadName);

                    Alert alert = Alert.builder()
                            .reference("SIM_OV_" + simulationTripId + "_" + uniqueSuffix)
                            .alertType("Over-speeding Alert")
                            .severity("CRITICAL")
                            .status("OPEN")
                            .vehicle(simulationVehicle)
                            .driver(simulationDriver)
                            .occurredAt(LocalDateTime.now())
                            .latitude(currentLatitude)
                            .longitude(currentLongitude)
                            .speedKmph(currentSpeedKmph)
                            .speedLimitKmph(currentSpeedLimitKmph)
                            .roadName(currentRoadName)
                            .accelerationMps2(currentAccelerationMps2)
                            .message(msg)
                            .isSimulation(true)
                            .deliveryStatus("PENDING")
                            .build();

                    persistAndRecordAlert(alert);
                    eventMessage = "CRITICAL ALERT GENERATED: Overspeeding detected at " + currentSpeedKmph + " km/h!";
                    break;
                }
                case "HARSH_BRAKING":
                case "HARSH_BRAKE": {
                    BigDecimal targetSpeed = currentSpeedKmph.compareTo(new BigDecimal("35.00")) > 0
                            ? currentSpeedKmph.subtract(new BigDecimal("30.00"))
                            : new BigDecimal("15.00");
                    previousSpeedKmph = currentSpeedKmph;
                    currentSpeedKmph = targetSpeed;
                    currentAccelerationMps2 = new BigDecimal("-4.20");
                    speedHistory.add(currentSpeedKmph);

                    String msg = String.format("Harsh braking event detected: speed dropped from %.1f to %.1f km/h (Deceleration: %.2f m/s²) on %s.",
                            previousSpeedKmph.doubleValue(), currentSpeedKmph.doubleValue(), currentAccelerationMps2.doubleValue(), currentRoadName);

                    Alert alert = Alert.builder()
                            .reference("SIM_HB_" + simulationTripId + "_" + uniqueSuffix)
                            .alertType("Harsh Braking Alert")
                            .severity("WARNING")
                            .status("OPEN")
                            .vehicle(simulationVehicle)
                            .driver(simulationDriver)
                            .occurredAt(LocalDateTime.now())
                            .latitude(currentLatitude)
                            .longitude(currentLongitude)
                            .speedKmph(currentSpeedKmph)
                            .previousSpeedKmph(previousSpeedKmph)
                            .speedLimitKmph(currentSpeedLimitKmph)
                            .roadName(currentRoadName)
                            .accelerationMps2(currentAccelerationMps2)
                            .message(msg)
                            .isSimulation(true)
                            .deliveryStatus("PENDING")
                            .build();

                    persistAndRecordAlert(alert);
                    eventMessage = "WARNING ALERT GENERATED: Harsh braking detected (" + currentAccelerationMps2 + " m/s²)!";
                    break;
                }
                case "HARSH_ACCEL":
                case "HARSH_ACCELERATION": {
                    BigDecimal targetSpeed = currentSpeedKmph.add(new BigDecimal("25.00"));
                    previousSpeedKmph = currentSpeedKmph;
                    currentSpeedKmph = targetSpeed;
                    currentAccelerationMps2 = new BigDecimal("3.80");
                    if (currentSpeedKmph.compareTo(maxSpeedRecorded) > 0) {
                        maxSpeedRecorded = currentSpeedKmph;
                    }
                    speedHistory.add(currentSpeedKmph);

                    String msg = String.format("Harsh acceleration event detected: speed surged from %.1f to %.1f km/h (Acceleration: %.2f m/s²) on %s.",
                            previousSpeedKmph.doubleValue(), currentSpeedKmph.doubleValue(), currentAccelerationMps2.doubleValue(), currentRoadName);

                    Alert alert = Alert.builder()
                            .reference("SIM_HA_" + simulationTripId + "_" + uniqueSuffix)
                            .alertType("Harsh Acceleration Alert")
                            .severity("WARNING")
                            .status("OPEN")
                            .vehicle(simulationVehicle)
                            .driver(simulationDriver)
                            .occurredAt(LocalDateTime.now())
                            .latitude(currentLatitude)
                            .longitude(currentLongitude)
                            .speedKmph(currentSpeedKmph)
                            .previousSpeedKmph(previousSpeedKmph)
                            .speedLimitKmph(currentSpeedLimitKmph)
                            .roadName(currentRoadName)
                            .accelerationMps2(currentAccelerationMps2)
                            .message(msg)
                            .isSimulation(true)
                            .deliveryStatus("PENDING")
                            .build();

                    persistAndRecordAlert(alert);
                    eventMessage = "WARNING ALERT GENERATED: Harsh acceleration detected (" + currentAccelerationMps2 + " m/s²)!";
                    break;
                }
                case "GPS_DISCONNECT": {
                    gpsConnected = false;
                    String msg = String.format("GPS device connection lost for simulated truck near %s (Last known: %.4f, %.4f).",
                            currentRoadName, currentLatitude.doubleValue(), currentLongitude.doubleValue());

                    Alert alert = Alert.builder()
                            .reference("SIM_GPS_" + simulationTripId + "_" + uniqueSuffix)
                            .alertType("GPS Disconnect Alert")
                            .severity("CRITICAL")
                            .status("OPEN")
                            .vehicle(simulationVehicle)
                            .driver(simulationDriver)
                            .occurredAt(LocalDateTime.now())
                            .latitude(currentLatitude)
                            .longitude(currentLongitude)
                            .speedKmph(currentSpeedKmph)
                            .speedLimitKmph(currentSpeedLimitKmph)
                            .roadName(currentRoadName)
                            .accelerationMps2(currentAccelerationMps2)
                            .message(msg)
                            .isSimulation(true)
                            .deliveryStatus("PENDING")
                            .build();

                    persistAndRecordAlert(alert);
                    eventMessage = "CRITICAL ALERT GENERATED: GPS signal disconnected!";
                    break;
                }
                case "RESTORE_GPS": {
                    gpsConnected = true;
                    eventMessage = "GPS signal successfully restored.";
                    break;
                }
                case "NORMAL":
                default: {
                    currentAccelerationMps2 = BigDecimal.ZERO;
                    eventMessage = "Resumed normal driving condition.";
                    break;
                }
            }

            if (Arrays.asList("OVERSPEED", "OVERSPEEDING", "HARSH_BRAKING", "HARSH_BRAKE", "HARSH_ACCEL", "HARSH_ACCELERATION", "GPS_DISCONNECT").contains(normalized)) {
                eventProcessingStatus = "ALERT_GENERATED";
            } else if ("NORMAL".equals(normalized)) {
                eventProcessingStatus = "NONE";
                recentAlerts.clear();
            } else {
                eventProcessingStatus = "TRIGGERED";
            }

            updateLiveTripInDb();
            log.info("Simulation manual event processed: {} [{}]", normalized, eventMessage);
            return getCurrentState();
        }
    }

    /**
     * Backward-compatible overload for triggerEvent.
     */
    public SimulationStateDTO triggerEvent(String eventType, BigDecimal overrideSpeed) {
        return triggerEvent(eventType, overrideSpeed, null);
    }

    /**
     * Advance the simulation by one step along the route.
     * Evaluates real telemetry and alert rules.
     */
    @Transactional
    public SimulationStateDTO stepSimulation() {
        synchronized (stateLock) {
            if (!"RUNNING".equals(status)) {
                return getCurrentState();
            }

            boolean keepAlertsFromTrigger = "ALERT_GENERATED".equals(eventProcessingStatus) && !recentAlerts.isEmpty();
            if (!keepAlertsFromTrigger) {
                recentAlerts.clear();
            }
            int totalWaypoints = currentRoute.getWaypoints().size();

            // Advance waypoint index
            if (currentWaypointIndex < totalWaypoints - 1) {
                currentWaypointIndex++;
            } else {
                // Journey completed!
                status = "COMPLETED";
                eventMessage = "Journey successfully completed! Arrived at destination: " + currentRoute.getDestination();
                finalizeTrip("COMPLETED");
                return getCurrentState();
            }

            SimulationWaypointDTO wp = currentRoute.getWaypoints().get(currentWaypointIndex);
            SimulationWaypointDTO prevWp = currentRoute.getWaypoints().get(currentWaypointIndex - 1);

            // Compute distance delta
            BigDecimal stepDistKm = calculateHaversineDistance(
                    prevWp.getLatitude().doubleValue(), prevWp.getLongitude().doubleValue(),
                    wp.getLatitude().doubleValue(), wp.getLongitude().doubleValue()
            );
            totalDistanceKm = totalDistanceKm.add(stepDistKm).setScale(2, RoundingMode.HALF_UP);

            // Time delta (typically 5-10s per waypoint, scaled by speed multiplier)
            int stepSeconds = Math.max(2, 6 / speedMultiplier);
            elapsedSeconds += stepSeconds;

            previousSpeedKmph = currentSpeedKmph;
            currentLatitude = wp.getLatitude();
            currentLongitude = wp.getLongitude();
            currentRoadName = wp.getRoadName();
            currentSpeedLimitKmph = wp.getSpeedLimitKmph();

            // Handle manual event or normal driving
            String eventToApply = (pendingEvent != null) ? pendingEvent : "NORMAL";
            pendingEvent = null; // consume one-shot trigger
            currentEvent = eventToApply;
            eventProcessingStatus = "PROCESSING";

            BigDecimal computedSpeed;
            boolean alertGeneratedThisStep = false;

            switch (eventToApply) {
                case "OVERSPEED":
                case "OVERSPEEDING":
                    // Exceed speed limit by 18-28 km/h
                    computedSpeed = currentSpeedLimitKmph.add(new BigDecimal("22.00"));
                    currentSpeedKmph = computedSpeed;
                    currentAccelerationMps2 = computeAcceleration(previousSpeedKmph, currentSpeedKmph, stepSeconds);
                    alertGeneratedThisStep = evaluateOverspeeding(wp);
                    break;

                case "HARSH_BRAKE":
                case "HARSH_BRAKING":
                    // Rapid deceleration: drop speed by 40-50 km/h in stepSeconds
                    BigDecimal targetDrop = new BigDecimal("42.00");
                    computedSpeed = previousSpeedKmph.subtract(targetDrop).max(new BigDecimal("15.00"));
                    currentSpeedKmph = computedSpeed;
                    currentAccelerationMps2 = computeAcceleration(previousSpeedKmph, currentSpeedKmph, stepSeconds);
                    alertGeneratedThisStep = evaluateHarshBraking(wp, previousSpeedKmph, currentSpeedKmph, currentAccelerationMps2);
                    break;

                case "HARSH_ACCEL":
                case "HARSH_ACCELERATION":
                    // Rapid acceleration: jump speed by 35 km/h
                    BigDecimal targetJump = new BigDecimal("35.00");
                    computedSpeed = previousSpeedKmph.add(targetJump).min(new BigDecimal("85.00"));
                    currentSpeedKmph = computedSpeed;
                    currentAccelerationMps2 = computeAcceleration(previousSpeedKmph, currentSpeedKmph, stepSeconds);
                    alertGeneratedThisStep = evaluateHarshAcceleration(wp, previousSpeedKmph, currentSpeedKmph, currentAccelerationMps2);
                    break;

                case "GPS_DISCONNECT":
                    gpsConnected = false;
                    computedSpeed = wp.getDefaultSpeedKmph();
                    currentSpeedKmph = computedSpeed;
                    currentAccelerationMps2 = computeAcceleration(previousSpeedKmph, currentSpeedKmph, stepSeconds);
                    alertGeneratedThisStep = evaluateGpsDisconnect(wp);
                    break;

                case "RESTORE_GPS":
                    gpsConnected = true;
                    currentEvent = "NORMAL";
                    computedSpeed = wp.getDefaultSpeedKmph();
                    currentSpeedKmph = computedSpeed;
                    currentAccelerationMps2 = computeAcceleration(previousSpeedKmph, currentSpeedKmph, stepSeconds);
                    eventProcessingStatus = "NONE";
                    eventMessage = "GPS signal recovered. Accurate telemetry restored.";
                    break;

                case "NORMAL":
                default:
                    // Normal driving within road speed limit
                    computedSpeed = wp.getDefaultSpeedKmph();
                    currentSpeedKmph = computedSpeed;
                    currentAccelerationMps2 = computeAcceleration(previousSpeedKmph, currentSpeedKmph, stepSeconds);
                    eventProcessingStatus = "NONE";
                    eventMessage = "Driving within configured speed limit (" + currentSpeedLimitKmph + " km/h). No alerts.";
                    break;
            }

            if (currentSpeedKmph.compareTo(maxSpeedRecorded) > 0) {
                maxSpeedRecorded = currentSpeedKmph;
            }
            speedHistory.add(currentSpeedKmph);

            if (alertGeneratedThisStep || keepAlertsFromTrigger) {
                eventProcessingStatus = "ALERT_GENERATED";
            } else if ("PROCESSING".equals(eventProcessingStatus)) {
                eventProcessingStatus = "THRESHOLD_NOT_MET";
            }

            // Check if final destination reached
            if (currentWaypointIndex >= totalWaypoints - 1) {
                status = "COMPLETED";
                eventMessage = "Journey successfully completed! Arrived at destination: " + currentRoute.getDestination();
                finalizeTrip("COMPLETED");
            } else {
                updateLiveTripInDb();
            }

            return getCurrentState();
        }
    }

    /**
     * Overspeeding Alert Rule Evaluation:
     * Condition: currentSpeed > speedLimit
     */
    private boolean evaluateOverspeeding(SimulationWaypointDTO wp) {
        if (currentSpeedKmph.compareTo(currentSpeedLimitKmph) > 0) {
            String deduplicationKey = "OVERSPEED_" + currentWaypointIndex;
            if (triggeredAlertKeysInSession.add(deduplicationKey)) {
                String msg = String.format("Vehicle exceeded speed limit: %.1f km/h (Limit: %.1f km/h) on %s.",
                        currentSpeedKmph.doubleValue(), currentSpeedLimitKmph.doubleValue(), wp.getRoadName());

                Alert alert = Alert.builder()
                        .reference("SIM_OV_" + simulationTripId + "_" + currentWaypointIndex + "_" + System.currentTimeMillis())
                        .alertType("Over-speeding Alert")
                        .severity("CRITICAL")
                        .status("OPEN")
                        .vehicle(simulationVehicle)
                        .driver(simulationDriver)
                        .occurredAt(LocalDateTime.now())
                        .latitude(currentLatitude)
                        .longitude(currentLongitude)
                        .speedKmph(currentSpeedKmph)
                        .speedLimitKmph(currentSpeedLimitKmph)
                        .roadName(wp.getRoadName())
                        .accelerationMps2(currentAccelerationMps2)
                        .message(msg)
                        .isSimulation(true)
                        .deliveryStatus("PENDING")
                        .build();

                persistAndRecordAlert(alert);
                eventMessage = "CRITICAL ALERT GENERATED: Overspeeding detected at " + currentSpeedKmph + " km/h!";
                return true;
            }
        }
        return false;
    }

    /**
     * Harsh Braking Alert Rule Evaluation:
     * Deceleration <= -3.5 m/s² OR speed drop >= 25 km/h
     */
    private boolean evaluateHarshBraking(SimulationWaypointDTO wp, BigDecimal prevSpeed, BigDecimal newSpeed, BigDecimal accel) {
        BigDecimal speedDrop = prevSpeed.subtract(newSpeed);
        if (accel.compareTo(new BigDecimal("-3.5")) <= 0 || speedDrop.compareTo(new BigDecimal("25.00")) >= 0) {
            String deduplicationKey = "HARSH_BRAKING_" + currentWaypointIndex;
            if (triggeredAlertKeysInSession.add(deduplicationKey)) {
                String msg = String.format("Harsh braking event detected: speed dropped from %.1f to %.1f km/h (Deceleration: %.2f m/s²) on %s.",
                        prevSpeed.doubleValue(), newSpeed.doubleValue(), accel.doubleValue(), wp.getRoadName());

                Alert alert = Alert.builder()
                        .reference("SIM_HB_" + simulationTripId + "_" + currentWaypointIndex + "_" + System.currentTimeMillis())
                        .alertType("Harsh Braking Alert")
                        .severity("WARNING")
                        .status("OPEN")
                        .vehicle(simulationVehicle)
                        .driver(simulationDriver)
                        .occurredAt(LocalDateTime.now())
                        .latitude(currentLatitude)
                        .longitude(currentLongitude)
                        .speedKmph(newSpeed)
                        .previousSpeedKmph(prevSpeed)
                        .speedLimitKmph(currentSpeedLimitKmph)
                        .roadName(wp.getRoadName())
                        .accelerationMps2(accel)
                        .message(msg)
                        .isSimulation(true)
                        .deliveryStatus("PENDING")
                        .build();

                persistAndRecordAlert(alert);
                eventMessage = "WARNING ALERT GENERATED: Harsh braking detected (" + accel + " m/s²)!";
                return true;
            }
        }
        return false;
    }

    /**
     * Harsh Acceleration Alert Rule Evaluation:
     * Acceleration >= 3.0 m/s² OR speed rise >= 20 km/h
     */
    private boolean evaluateHarshAcceleration(SimulationWaypointDTO wp, BigDecimal prevSpeed, BigDecimal newSpeed, BigDecimal accel) {
        BigDecimal speedRise = newSpeed.subtract(prevSpeed);
        if (accel.compareTo(new BigDecimal("3.0")) >= 0 || speedRise.compareTo(new BigDecimal("20.00")) >= 0) {
            String deduplicationKey = "HARSH_ACCEL_" + currentWaypointIndex;
            if (triggeredAlertKeysInSession.add(deduplicationKey)) {
                String msg = String.format("Harsh acceleration event detected: speed surged from %.1f to %.1f km/h (Acceleration: %.2f m/s²) on %s.",
                        prevSpeed.doubleValue(), newSpeed.doubleValue(), accel.doubleValue(), wp.getRoadName());

                Alert alert = Alert.builder()
                        .reference("SIM_HA_" + simulationTripId + "_" + currentWaypointIndex + "_" + System.currentTimeMillis())
                        .alertType("Harsh Acceleration Alert")
                        .severity("WARNING")
                        .status("OPEN")
                        .vehicle(simulationVehicle)
                        .driver(simulationDriver)
                        .occurredAt(LocalDateTime.now())
                        .latitude(currentLatitude)
                        .longitude(currentLongitude)
                        .speedKmph(newSpeed)
                        .previousSpeedKmph(prevSpeed)
                        .speedLimitKmph(currentSpeedLimitKmph)
                        .roadName(wp.getRoadName())
                        .accelerationMps2(accel)
                        .message(msg)
                        .isSimulation(true)
                        .deliveryStatus("PENDING")
                        .build();

                persistAndRecordAlert(alert);
                eventMessage = "WARNING ALERT GENERATED: Harsh acceleration detected (" + accel + " m/s²)!";
                return true;
            }
        }
        return false;
    }

    /**
     * GPS Disconnection Alert Rule Evaluation:
     * Condition: !gpsConnected
     */
    private boolean evaluateGpsDisconnect(SimulationWaypointDTO wp) {
        String deduplicationKey = "GPS_DISCONNECT_" + currentWaypointIndex;
        if (triggeredAlertKeysInSession.add(deduplicationKey)) {
            String msg = String.format("GPS device connection lost for simulated truck near %s (Last known: %.4f, %.4f).",
                    wp.getRoadName(), currentLatitude.doubleValue(), currentLongitude.doubleValue());

            Alert alert = Alert.builder()
                    .reference("SIM_GPS_" + simulationTripId + "_" + currentWaypointIndex + "_" + System.currentTimeMillis())
                    .alertType("GPS Disconnect Alert")
                    .severity("CRITICAL")
                    .status("OPEN")
                    .vehicle(simulationVehicle)
                    .driver(simulationDriver)
                    .occurredAt(LocalDateTime.now())
                    .latitude(currentLatitude)
                    .longitude(currentLongitude)
                    .speedKmph(currentSpeedKmph)
                    .speedLimitKmph(currentSpeedLimitKmph)
                    .roadName(wp.getRoadName())
                    .accelerationMps2(currentAccelerationMps2)
                    .message(msg)
                    .isSimulation(true)
                    .deliveryStatus("PENDING")
                    .build();

            persistAndRecordAlert(alert);
            eventMessage = "CRITICAL ALERT GENERATED: GPS signal disconnected!";
            return true;
        }
        return false;
    }

    /**
     * Persist alert via AlertService (which automatically evaluates progressive penalties and bypasses external emails/SMS).
     */
    private void persistAndRecordAlert(Alert alert) {
        if (simulationTripId != null) {
            tripRepository.findById(simulationTripId).ifPresent(alert::setTrip);
        }
        Alert saved = alertService.createAlert(alert);
        AlertDTO dto = alertService.getById(saved.getId());
        recentAlerts.add(dto);
        allAlerts.add(dto);
        log.info("Simulation alert recorded: {} (Occurrence #{}, Penalty: Rs. {})",
                dto.getAlertType(), dto.getOccurrenceNumber(), dto.getPenaltyAmount());
    }

    private void updateLiveTripInDb() {
        if (simulationTripId == null) return;
        tripRepository.findById(simulationTripId).ifPresent(trip -> {
            trip.setDistanceKm(totalDistanceKm);
            trip.setDurationSeconds((int) elapsedSeconds);
            trip.setMaxSpeedKmph(maxSpeedRecorded);
            trip.setAverageSpeedKmph(calculateAverageSpeed());
            trip.setAlertCount(allAlerts.size());
            trip.setPrimaryRoad(currentRoadName);
            tripRepository.save(trip);
        });
    }

    private void finalizeTrip(String completionStatus) {
        if (simulationTripId == null) return;
        tripRepository.findById(simulationTripId).ifPresent(trip -> {
            trip.setStatus(completionStatus);
            trip.setEndTime(LocalDateTime.now());
            trip.setEndLatitude(currentLatitude);
            trip.setEndLongitude(currentLongitude);
            trip.setDistanceKm(totalDistanceKm);
            trip.setDurationSeconds((int) elapsedSeconds);
            trip.setMaxSpeedKmph(maxSpeedRecorded);
            BigDecimal avgSpeed = calculateAverageSpeed();
            trip.setAverageSpeedKmph(avgSpeed);
            trip.setAlertCount(allAlerts.size());
            tripRepository.save(trip);

            // Compute final summary
            Map<String, Long> alertBreakdown = new HashMap<>();
            BigDecimal totalPenalty = BigDecimal.ZERO;
            for (AlertDTO a : allAlerts) {
                alertBreakdown.put(a.getAlertType(), alertBreakdown.getOrDefault(a.getAlertType(), 0L) + 1);
                if (a.getPenaltyAmount() != null) {
                    totalPenalty = totalPenalty.add(a.getPenaltyAmount());
                }
            }

            long mins = elapsedSeconds / 60;
            long secs = elapsedSeconds % 60;
            String durationFormatted = String.format("%02d min %02d sec", mins, secs);

            finalSummary = SimulationSummaryDTO.builder()
                    .startingLocation(currentRoute != null ? currentRoute.getOrigin() : "Origin")
                    .destination(currentRoute != null ? currentRoute.getDestination() : "Destination")
                    .totalSimulatedDistanceKm(totalDistanceKm)
                    .tripDurationSeconds(elapsedSeconds)
                    .tripDurationFormatted(durationFormatted)
                    .maxSpeedKmph(maxSpeedRecorded.setScale(1, RoundingMode.HALF_UP))
                    .averageSpeedKmph(avgSpeed.setScale(1, RoundingMode.HALF_UP))
                    .totalAlerts(allAlerts.size())
                    .alertBreakdownByType(alertBreakdown)
                    .totalPenaltyAmount(totalPenalty.setScale(2, RoundingMode.HALF_UP))
                    .tripCompletionStatus(completionStatus)
                    .tripId(simulationTripId)
                    .vehicleRegistrationNumber(simulationVehicle != null ? simulationVehicle.getRegistrationNumber() : "MH01SM9999")
                    .driverName(NotificationService.SIMULATION_DRIVER_NAME)
                    .build();
        });
    }

    public SimulationStateDTO getCurrentState() {
        if (currentRoute == null) {
            currentRoute = routeProvider.getRoute("mumbai-pune-expressway");
        }
        int totalWp = currentRoute.getWaypoints().size();
        double progress = totalWp > 1 ? (double) currentWaypointIndex / (totalWp - 1) * 100.0 : 0.0;

        BigDecimal totalPenalties = BigDecimal.ZERO;
        for (AlertDTO a : allAlerts) {
            if (a.getPenaltyAmount() != null) {
                totalPenalties = totalPenalties.add(a.getPenaltyAmount());
            }
        }

        return SimulationStateDTO.builder()
                .status(status)
                .routeId(currentRoute.getId())
                .routeName(currentRoute.getName())
                .origin(currentRoute.getOrigin())
                .destination(currentRoute.getDestination())
                .currentWaypointIndex(currentWaypointIndex)
                .totalWaypoints(totalWp)
                .progressPercent(Math.round(progress * 10.0) / 10.0)
                .vehicleId(simulationVehicle != null ? simulationVehicle.getId() : null)
                .vehicleCode(simulationVehicle != null ? simulationVehicle.getCode() : "VH_SIM_01")
                .vehicleRegistrationNumber(simulationVehicle != null ? simulationVehicle.getRegistrationNumber() : "MH01SM9999")
                .vehicleType(simulationVehicle != null ? simulationVehicle.getVehicleType() : "Heavy Truck")
                .makeModel(simulationVehicle != null ? simulationVehicle.getMakeModel() : "BharatBenz 2823R Heavy Truck")
                .driverId(simulationDriver != null ? simulationDriver.getId() : null)
                .driverName(NotificationService.SIMULATION_DRIVER_NAME)
                .driverCode(simulationDriver != null ? simulationDriver.getCode() : "DRV_SIM_01")
                .latitude(currentLatitude)
                .longitude(currentLongitude)
                .roadName(currentRoadName)
                .currentSpeedKmph(currentSpeedKmph.setScale(1, RoundingMode.HALF_UP))
                .speedLimitKmph(currentSpeedLimitKmph.setScale(1, RoundingMode.HALF_UP))
                .accelerationMps2(currentAccelerationMps2.setScale(2, RoundingMode.HALF_UP))
                .distanceTravelledKm(totalDistanceKm.setScale(2, RoundingMode.HALF_UP))
                .elapsedSeconds(elapsedSeconds)
                .gpsConnected(gpsConnected)
                .currentEvent(currentEvent)
                .eventProcessingStatus(eventProcessingStatus)
                .eventMessage(eventMessage)
                .recentAlerts(new ArrayList<>(recentAlerts))
                .allAlerts(new ArrayList<>(allAlerts))
                .totalAlertsCount(allAlerts.size())
                .totalPenaltyAmount(totalPenalties.setScale(2, RoundingMode.HALF_UP))
                .simulationTripId(simulationTripId)
                .summary(finalSummary)
                .build();
    }

    public SimulationSummaryDTO getSimulationSummary() {
        return finalSummary;
    }

    private BigDecimal computeAcceleration(BigDecimal prevSpeed, BigDecimal newSpeed, int seconds) {
        if (seconds <= 0) return BigDecimal.ZERO;
        double v1 = prevSpeed.doubleValue() * 1000.0 / 3600.0;
        double v2 = newSpeed.doubleValue() * 1000.0 / 3600.0;
        double a = (v2 - v1) / seconds;
        return BigDecimal.valueOf(a).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverageSpeed() {
        if (speedHistory.isEmpty()) return BigDecimal.ZERO;
        double sum = 0;
        for (BigDecimal s : speedHistory) {
            sum += s.doubleValue();
        }
        return BigDecimal.valueOf(sum / speedHistory.size()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the Earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(R * c).setScale(3, RoundingMode.HALF_UP);
    }

    private Vehicle getOrCreateSimulationVehicle() {
        return vehicleRepository.findByCode("VH_SIM_01")
                .orElseGet(() -> {
                    Driver simDriver = getOrCreateSimulationDriver();
                    Vehicle v = Vehicle.builder()
                            .code("VH_SIM_01")
                            .registrationNumber("MH01SM9999")
                            .vehicleType("Heavy Truck")
                            .makeModel("BharatBenz 2823R Heavy Truck")
                            .fuelType("Diesel")
                            .tankCapacityLitres(new BigDecimal("380.00"))
                            .ratedMileageKmpl(new BigDecimal("4.50"))
                            .odometerKm(new BigDecimal("14250.00"))
                            .driver(simDriver)
                            .active(true)
                            .build();
                    return vehicleRepository.save(v);
                });
    }

    private Driver getOrCreateSimulationDriver() {
        return driverRepository.findByCode("DRV_SIM_01")
                .orElseGet(() -> {
                    Driver d = Driver.builder()
                            .code("DRV_SIM_01")
                            .name(NotificationService.SIMULATION_DRIVER_NAME)
                            .phone("9999900001")
                            .email(NotificationService.SIMULATION_DRIVER_EMAIL)
                            .licenseNumber("MH-01-2024-SIM001")
                            .active(true)
                            .build();
                    return driverRepository.save(d);
                });
    }
}
