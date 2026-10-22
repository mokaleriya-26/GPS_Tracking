package com.gps.tracking.service;

import com.gps.tracking.dto.AlertDTO;
import com.gps.tracking.dto.ChatResponseDTO;
import com.gps.tracking.dto.SimulationStateDTO;
import com.gps.tracking.dto.SimulationSummaryDTO;
import com.gps.tracking.entity.Alert;
import com.gps.tracking.entity.Trip;
import com.gps.tracking.repository.AlertRepository;
import com.gps.tracking.repository.TripRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SimulationServiceTest {

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TripRepository tripRepository;

    @BeforeEach
    public void setup() {
        simulationService.resetSimulation();
        jakarta.mail.internet.MimeMessage mimeMessage = new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null);
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @AfterEach
    public void tearDown() {
        simulationService.resetSimulation();
    }

    @Test
    @DisplayName("1. Starting a simulation initializes state, truck position, and persistent Trip")
    public void testStartSimulation() {
        SimulationStateDTO state = simulationService.startSimulation("mumbai-pune-expressway", 1);
        assertNotNull(state);
        assertEquals("RUNNING", state.getStatus());
        assertEquals("mumbai-pune-expressway", state.getRouteId());
        assertEquals(0, state.getCurrentWaypointIndex());
        assertNotNull(state.getSimulationTripId());
        assertNotNull(state.getVehicleRegistrationNumber());
        assertEquals("MH01SM9999", state.getVehicleRegistrationNumber());
        assertTrue(state.isGpsConnected());

        // Verify Trip in database has is_simulation = true
        Trip trip = tripRepository.findById(state.getSimulationTripId()).orElse(null);
        assertNotNull(trip);
        assertTrue(Boolean.TRUE.equals(trip.getIsSimulation()));
        assertEquals("ACTIVE", trip.getStatus());
    }

    @Test
    @DisplayName("2. Pausing and Resuming a simulation preserves journey progress")
    public void testPauseAndResumeSimulation() {
        simulationService.startSimulation("mumbai-pune-expressway", 1);
        simulationService.stepSimulation(); // advance to waypoint 1

        SimulationStateDTO paused = simulationService.pauseSimulation();
        assertEquals("PAUSED", paused.getStatus());
        assertEquals(1, paused.getCurrentWaypointIndex());

        // Step while paused should not advance
        SimulationStateDTO afterStepWhilePaused = simulationService.stepSimulation();
        assertEquals("PAUSED", afterStepWhilePaused.getStatus());
        assertEquals(1, afterStepWhilePaused.getCurrentWaypointIndex());

        SimulationStateDTO resumed = simulationService.resumeSimulation();
        assertEquals("RUNNING", resumed.getStatus());
        assertEquals(1, resumed.getCurrentWaypointIndex());

        // Step after resume should advance
        SimulationStateDTO advanced = simulationService.stepSimulation();
        assertEquals(2, advanced.getCurrentWaypointIndex());
    }

    @Test
    @DisplayName("3. Stopping a simulation sets STOPPED status and finalizes trip summary")
    public void testStopSimulation() {
        simulationService.startSimulation("mumbai-pune-expressway", 2);
        simulationService.stepSimulation();
        simulationService.stepSimulation();

        SimulationStateDTO stopped = simulationService.stopSimulation();
        assertEquals("STOPPED", stopped.getStatus());
        assertNotNull(stopped.getSummary());
        assertEquals("STOPPED", stopped.getSummary().getTripCompletionStatus());
    }

    @Test
    @DisplayName("4. Updating truck position and coordinates along route waypoints")
    public void testTruckPositionUpdate() {
        SimulationStateDTO initial = simulationService.startSimulation("mumbai-pune-expressway", 1);
        BigDecimal initialLat = initial.getLatitude();
        BigDecimal initialLng = initial.getLongitude();

        SimulationStateDTO stepped = simulationService.stepSimulation();
        assertNotEquals(initialLat, stepped.getLatitude());
        assertNotEquals(initialLng, stepped.getLongitude());
        assertTrue(stepped.getDistanceTravelledKm().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("5. Updating simulated speed and telemetry stats")
    public void testSimulatedSpeedUpdate() {
        simulationService.startSimulation("mumbai-pune-expressway", 1);
        SimulationStateDTO step1 = simulationService.stepSimulation();
        assertNotNull(step1.getCurrentSpeedKmph());
        assertTrue(step1.getCurrentSpeedKmph().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(step1.getAccelerationMps2());
    }

    @Test
    @DisplayName("6. Generating Overspeed alert when speed exceeds threshold with progressive penalty")
    public void testOverspeedAlertGeneration() {
        simulationService.startSimulation("mumbai-pune-expressway", 1);

        // Queue overspeed scenario
        simulationService.triggerEvent("OVERSPEED", null);
        SimulationStateDTO stepped = simulationService.stepSimulation();

        assertEquals("ALERT_GENERATED", stepped.getEventProcessingStatus());
        assertFalse(stepped.getRecentAlerts().isEmpty());

        boolean hasOverspeed = stepped.getRecentAlerts().stream()
                .anyMatch(a -> a.getAlertType().contains("Over-speed") || a.getAlertType().contains("Overspeed"));
        assertTrue(hasOverspeed, "Expected overspeed alert to be recorded");

        // Verify alert in DB has is_simulation = true
        List<Alert> simAlerts = alertRepository.findByTripId(stepped.getSimulationTripId());
        assertFalse(simAlerts.isEmpty());
        Alert alert = simAlerts.get(0);
        assertTrue(Boolean.TRUE.equals(alert.getIsSimulation()));
        assertTrue("PENDING".equals(alert.getDeliveryStatus()) || "SENT".equals(alert.getDeliveryStatus()));

        // Progressive penalty occurrence check
        assertEquals(1, stepped.getRecentAlerts().get(0).getOccurrenceNumber());
        assertEquals("WARNING", stepped.getRecentAlerts().get(0).getPenaltyClassification());
    }

    @Test
    @DisplayName("7. Generating Harsh Braking alert when rapid deceleration threshold is met")
    public void testHarshBrakingAlertGeneration() {
        simulationService.startSimulation("mumbai-pune-expressway", 1);
        simulationService.stepSimulation(); // establish cruising speed

        // Trigger harsh braking scenario
        simulationService.triggerEvent("HARSH_BRAKING", null);
        SimulationStateDTO stepped = simulationService.stepSimulation();

        assertEquals("ALERT_GENERATED", stepped.getEventProcessingStatus());
        boolean hasHarshBraking = stepped.getRecentAlerts().stream()
                .anyMatch(a -> a.getAlertType().contains("Harsh Braking"));
        assertTrue(hasHarshBraking, "Expected Harsh Braking alert to be generated");
    }

    @Test
    @DisplayName("8. Simulating GPS disconnection and recovery")
    public void testGpsDisconnectAndRecovery() {
        simulationService.startSimulation("mumbai-pune-expressway", 1);

        // Trigger GPS disconnect
        simulationService.triggerEvent("GPS_DISCONNECT", null);
        SimulationStateDTO disconnected = simulationService.stepSimulation();

        assertFalse(disconnected.isGpsConnected());
        boolean hasGpsAlert = disconnected.getRecentAlerts().stream()
                .anyMatch(a -> a.getAlertType().contains("GPS Disconnect"));
        assertTrue(hasGpsAlert, "Expected GPS Disconnect Alert when signal is lost");

        // Restore GPS
        simulationService.triggerEvent("RESTORE_GPS", null);
        SimulationStateDTO restored = simulationService.stepSimulation();
        assertTrue(restored.isGpsConnected(), "GPS connection should be restored");
    }

    @Test
    @DisplayName("9. Driving normally produces zero false alerts")
    public void testNormalDrivingNoAlerts() {
        simulationService.startSimulation("mumbai-pune-expressway", 1);
        simulationService.triggerEvent("NORMAL", null);
        SimulationStateDTO stepped = simulationService.stepSimulation();

        assertTrue(stepped.getRecentAlerts().isEmpty(), "Normal driving should not trigger false alerts");
        assertEquals("NONE", stepped.getEventProcessingStatus());
    }

    @Test
    @DisplayName("10. Preventing duplicate alert creation for the same waypoint event")
    public void testPreventDuplicateAlertCreation() {
        simulationService.startSimulation("mumbai-pune-expressway", 1);
        simulationService.triggerEvent("OVERSPEED", null);
        simulationService.stepSimulation();

        int alertCountAfterFirst = simulationService.getCurrentState().getTotalAlertsCount();

        // If another step is normal, count should not duplicate the prior alert
        simulationService.triggerEvent("NORMAL", null);
        simulationService.stepSimulation();
        int alertCountAfterSecond = simulationService.getCurrentState().getTotalAlertsCount();

        assertEquals(alertCountAfterFirst, alertCountAfterSecond);
    }

    @Test
    @DisplayName("11. Preserving strict separation between simulation and production data")
    public void testSimulationDataSeparation() {
        SimulationStateDTO simState = simulationService.startSimulation("mumbai-pune-expressway", 1);
        simulationService.triggerEvent("OVERSPEED", null);
        simulationService.stepSimulation();

        List<Alert> simAlerts = alertRepository.findByTripIdAndIsSimulationTrue(simState.getSimulationTripId());
        assertFalse(simAlerts.isEmpty(), "Simulation alerts must be marked is_simulation=true");

        for (Alert a : simAlerts) {
            assertTrue(Boolean.TRUE.equals(a.getIsSimulation()));
            assertNotEquals("PRODUCTION", a.getDeliveryStatus());
        }
    }

    @Test
    @DisplayName("12. Preventing SMS notifications during simulation alerts while verifying simulation driver Justin Sunil")
    public void testSimulationAlertNotificationPolicy() {
        simulationService.startSimulation("mumbai-pune-expressway", 1);
        simulationService.triggerEvent("OVERSPEED", null);
        SimulationStateDTO stepped = simulationService.stepSimulation();

        AlertDTO alert = stepped.getRecentAlerts().get(0);
        assertFalse(Boolean.TRUE.equals(alert.getSmsSent()), "SMS must never be sent for simulation alerts");
        assertEquals(NotificationService.SIMULATION_DRIVER_NAME, alert.getDriverName(), "Driver name must be Justin Sunil for simulation");
        assertEquals(NotificationService.SIMULATION_DRIVER_EMAIL, alert.getDriverEmail(), "Driver email must be justinsunilsunil@gmail.com for simulation");
    }

    @Test
    @DisplayName("13. Safe cleanup during simulation reset without deleting production data")
    public void testSafeResetCleanup() {
        SimulationStateDTO simState = simulationService.startSimulation("mumbai-pune-expressway", 1);
        Long tripId = simState.getSimulationTripId();
        simulationService.triggerEvent("OVERSPEED", null);
        simulationService.stepSimulation();

        // Ensure alert exists
        assertFalse(alertRepository.findByTripId(tripId).isEmpty());

        // Reset
        simulationService.resetSimulation();

        // Verify simulation trip and its alerts are safely cleared
        assertTrue(tripRepository.findById(tripId).isEmpty());
        assertTrue(alertRepository.findByTripId(tripId).isEmpty());
    }

    @Test
    @DisplayName("14. Chatbot correctly answers queries about current simulation state")
    public void testChatbotSimulationQueries() {
        simulationService.startSimulation("mumbai-pune-expressway", 1);
        simulationService.stepSimulation();

        // Speed query
        ChatResponseDTO speedResp = chatService.processMessage("sim-test-session", "What is the simulated truck's current speed?");
        assertNotNull(speedResp);
        assertEquals("SIMULATION_QUERY", speedResp.getIntent());
        assertTrue(speedResp.getMessage().contains("Simulated Truck Current Speed"));
        assertTrue(speedResp.getMessage().contains("km/h"));

        // Distance query
        ChatResponseDTO distResp = chatService.processMessage("sim-test-session", "How far has the truck travelled in simulation?");
        assertNotNull(distResp);
        assertTrue(distResp.getMessage().contains("Distance Travelled"));

        // Max speed query
        ChatResponseDTO maxSpeedResp = chatService.processMessage("sim-test-session", "What was the maximum speed in simulation?");
        assertNotNull(maxSpeedResp);
        assertTrue(maxSpeedResp.getMessage().contains("Simulated Maximum Speed"));
    }
}
