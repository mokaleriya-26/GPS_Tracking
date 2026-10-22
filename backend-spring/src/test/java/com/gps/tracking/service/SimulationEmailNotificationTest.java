package com.gps.tracking.service;

import com.gps.tracking.dto.SimulationStateDTO;
import com.gps.tracking.entity.Alert;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.entity.Vehicle;
import com.gps.tracking.repository.AlertRepository;
import com.gps.tracking.repository.DriverRepository;
import com.gps.tracking.repository.VehicleRepository;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verification test suite for Live Simulation Email Notifications:
 *
 * TEST 1: Harsh braking alert in simulation -> recipients riyamokale@gmail.com, rhea260906@gmail.com, justinsunilsunil@gmail.com
 * TEST 2: Overspeeding alert in simulation -> all 3 simulation recipients receive the alert notification
 * TEST 3: Normal non-simulation alert -> driver email retrieved from PostgreSQL, existing normal behavior unchanged
 * TEST 4: Backend restart / existing historical alerts -> no historical simulation emails resent
 * TEST 5: Processing the same simulated alert twice -> no duplicate notification sent
 */
@SpringBootTest
public class SimulationEmailNotificationTest {

    private static final Logger log = LoggerFactory.getLogger(SimulationEmailNotificationTest.class);

    private static final String FLEET_EMAIL = "riyamokale@gmail.com";
    private static final String MANAGER_EMAIL = "rhea260906@gmail.com";
    private static final String SIM_DRIVER_EMAIL = "justinsunilsunil@gmail.com";
    private static final String SIM_DRIVER_NAME = "Justin Sunil";

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    private Vehicle testVehicle;

    @BeforeEach
    public void setup() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        simulationService.resetSimulation();

        testVehicle = vehicleRepository.findById(1L).orElseGet(() ->
                vehicleRepository.save(Vehicle.builder()
                        .code("VH_SIM_TEST")
                        .registrationNumber("MH01SM9999")
                        .vehicleType("Heavy Truck")
                        .makeModel("BharatBenz 2823R")
                        .active(true)
                        .build()));
    }

    @AfterEach
    public void tearDown() {
        simulationService.resetSimulation();
    }

    @Test
    @DisplayName("TEST 1: Trigger a simulated harsh braking alert -> Emails dispatched to Fleet, Manager, and Justin Sunil")
    public void test1_simulatedHarshBrakingAlertRecipients() throws Exception {
        log.info("=== TEST 1: Triggering Simulated Harsh Braking Alert ===");
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        // Capture all real driver emails in PostgreSQL before the test
        List<String> realDriverEmailsBefore = driverRepository.findAll().stream()
                .map(Driver::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .toList();

        // Start simulation and trigger harsh braking
        simulationService.startSimulation("mumbai-pune-expressway", 1);
        simulationService.stepSimulation(); // step to reach speed
        simulationService.triggerEvent("HARSH_BRAKING", null);
        SimulationStateDTO state = simulationService.stepSimulation();

        assertFalse(state.getRecentAlerts().isEmpty(), "Harsh braking alert should be generated");
        Long simAlertId = state.getRecentAlerts().get(0).getId();
        assertNotNull(simAlertId);

        Alert simAlert = alertRepository.findById(simAlertId).orElseThrow();
        assertTrue(Boolean.TRUE.equals(simAlert.getIsSimulation()), "Alert must have isSimulation=true");

        // Verify mailSender received exactly 3 messages via asynchronous delivery
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(3000).times(3)).send(captor.capture());

        List<MimeMessage> sentMessages = captor.getAllValues();
        List<String> recipients = sentMessages.stream()
                .map(m -> {
                    try {
                        Address[] to = m.getRecipients(Message.RecipientType.TO);
                        return to != null && to.length > 0 ? to[0].toString().toLowerCase() : "";
                    } catch (Exception e) {
                        return "";
                    }
                })
                .toList();

        log.info("TEST 1 Captured email recipients: {}", recipients);
        assertTrue(recipients.contains(FLEET_EMAIL), "Fleet email riyamokale@gmail.com must be recipient");
        assertTrue(recipients.contains(MANAGER_EMAIL), "Manager email rhea260906@gmail.com must be recipient");
        assertTrue(recipients.contains(SIM_DRIVER_EMAIL), "Simulation driver email justinsunilsunil@gmail.com must be recipient");

        // Verify subject contains [TrackFleet Simulation]
        for (MimeMessage msg : sentMessages) {
            String subject = msg.getSubject();
            log.info("TEST 1 Email Subject: {}", subject);
            assertTrue(subject.startsWith("[TrackFleet Simulation]"), "Subject must have [TrackFleet Simulation] prefix");
            assertTrue(subject.contains("Harsh Braking Alert"));
        }

        // Verify SMS is NOT sent for simulation
        assertFalse(Boolean.TRUE.equals(simAlert.getSmsSent()));
        assertFalse(Boolean.TRUE.equals(simAlert.getFleetSmsSent()));
        assertFalse(Boolean.TRUE.equals(simAlert.getManagerSmsSent()));
        assertFalse(Boolean.TRUE.equals(simAlert.getDriverSmsSent()));

        // Verify no PostgreSQL driver records were modified
        List<String> realDriverEmailsAfter = driverRepository.findAll().stream()
                .map(Driver::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .toList();
        assertEquals(realDriverEmailsBefore, realDriverEmailsAfter, "PostgreSQL driver records must not be modified");
    }

    @Test
    @DisplayName("TEST 2: Trigger a simulated overspeeding alert -> All three simulation recipients receive notification")
    public void test2_simulatedOverspeedingAlertRecipients() throws Exception {
        log.info("=== TEST 2: Triggering Simulated Overspeeding Alert ===");
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        simulationService.startSimulation("mumbai-pune-expressway", 1);
        simulationService.triggerEvent("OVERSPEED", null);
        SimulationStateDTO state = simulationService.stepSimulation();

        assertFalse(state.getRecentAlerts().isEmpty(), "Overspeed alert should be generated");
        Long simAlertId = state.getRecentAlerts().get(0).getId();
        assertNotNull(simAlertId);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(3000).times(3)).send(captor.capture());

        List<String> recipients = captor.getAllValues().stream()
                .map(m -> {
                    try {
                        Address[] to = m.getRecipients(Message.RecipientType.TO);
                        return to != null && to.length > 0 ? to[0].toString().toLowerCase() : "";
                    } catch (Exception e) {
                        return "";
                    }
                })
                .toList();

        log.info("TEST 2 Captured email recipients: {}", recipients);
        assertTrue(recipients.contains(FLEET_EMAIL));
        assertTrue(recipients.contains(MANAGER_EMAIL));
        assertTrue(recipients.contains(SIM_DRIVER_EMAIL));

        for (MimeMessage msg : captor.getAllValues()) {
            assertTrue(msg.getSubject().startsWith("[TrackFleet Simulation]"));
        }
    }

    @Test
    @DisplayName("TEST 3: Create normal non-simulation alert -> Driver email retrieved from PostgreSQL, normal behavior unchanged")
    public void test3_normalAlertUsesDatabaseDriverEmail() throws Exception {
        log.info("=== TEST 3: Creating Normal Non-Simulation Alert ===");
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        String normalDriverEmail = "rajesh.sharma.test@fleettrack.io";
        String normalDriverName = "Rajesh Sharma";

        Driver dbDriver = driverRepository.save(Driver.builder()
                .code("DRV_NORM_" + System.currentTimeMillis())
                .name(normalDriverName)
                .email(normalDriverEmail)
                .phone("9876543210")
                .active(true)
                .build());

        Alert normalAlert = alertService.createAlert(Alert.builder()
                .reference("NORMAL_ALERT_" + System.currentTimeMillis())
                .alertType("Over-speeding Alert")
                .severity("CRITICAL")
                .status("OPEN")
                .vehicle(testVehicle)
                .driver(dbDriver)
                .occurredAt(LocalDateTime.now())
                .speedKmph(BigDecimal.valueOf(95.0))
                .speedLimitKmph(BigDecimal.valueOf(80.0))
                .roadName("National Highway 48")
                .message("Normal non-simulation overspeed violation")
                .isSimulation(false)
                .deliveryStatus("PENDING")
                .build());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(3000).times(3)).send(captor.capture());

        List<String> recipients = captor.getAllValues().stream()
                .map(m -> {
                    try {
                        Address[] to = m.getRecipients(Message.RecipientType.TO);
                        return to != null && to.length > 0 ? to[0].toString().toLowerCase() : "";
                    } catch (Exception e) {
                        return "";
                    }
                })
                .toList();

        log.info("TEST 3 Captured normal recipients: {}", recipients);
        assertTrue(recipients.contains(FLEET_EMAIL));
        assertTrue(recipients.contains(MANAGER_EMAIL));
        assertTrue(recipients.contains(normalDriverEmail), "Normal alert must use the PostgreSQL driver's email");
        assertFalse(recipients.contains(SIM_DRIVER_EMAIL), "Normal alert must NOT use Justin Sunil's email");

        // Normal alert subject should have standard [TrackFleet] prefix without 'Simulation'
        for (MimeMessage msg : captor.getAllValues()) {
            String subject = msg.getSubject();
            log.info("TEST 3 Normal Subject: {}", subject);
            assertTrue(subject.startsWith("[TrackFleet] "));
            assertFalse(subject.contains("[TrackFleet Simulation]"));
        }
    }

    @Test
    @DisplayName("TEST 4: Backend restart with existing historical alerts -> No historical emails resent")
    public void test4_historicalAlertsNotResent() {
        log.info("=== TEST 4: Verifying Historical Alerts Are Silenced ===");
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        Alert historicalAlert = alertRepository.save(Alert.builder()
                .reference("HIST_ALERT_" + System.currentTimeMillis())
                .alertType("Harsh Braking Alert")
                .severity("WARNING")
                .status("HISTORICAL")
                .deliveryStatus("SILENCED")
                .vehicle(testVehicle)
                .occurredAt(LocalDateTime.now().minusDays(1))
                .isSimulation(true)
                .build());

        notificationService.processAlertSync(historicalAlert, List.of("Harsh Braking Alert"), true);

        // Verify mailSender.send was NEVER called
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("TEST 5: Process the same simulated alert twice -> No duplicate notification sent")
    public void test5_duplicateProcessingPrevented() {
        log.info("=== TEST 5: Preventing Duplicate Alert Email Dispatch ===");
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        Alert simAlert = alertRepository.save(Alert.builder()
                .reference("SIM_DUP_" + System.currentTimeMillis())
                .alertType("Harsh Braking Alert")
                .severity("WARNING")
                .status("OPEN")
                .deliveryStatus("PENDING")
                .vehicle(testVehicle)
                .occurredAt(LocalDateTime.now())
                .isSimulation(true)
                .build());

        // First execution: should send 3 emails
        notificationService.processAlertSync(simAlert, List.of("Harsh Braking Alert"), false);
        verify(mailSender, times(3)).send(any(MimeMessage.class));

        // Reload alert to verify emailSent flag is true
        Alert reloaded = alertRepository.findById(simAlert.getId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(reloaded.getEmailSent()));

        // Second execution with same alert: duplicate must be blocked
        notificationService.processAlertSync(reloaded, List.of("Harsh Braking Alert"), false);

        // mailSender should STILL have been called only 3 times in total, not 6
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("TEST 6: Four consecutive Harsh Braking deliberate triggers -> 4 distinct saved alerts, 4 separate email rounds (12 messages), and progressive penalties")
    public void test6_consecutiveRepeatedAlertClicks() {
        log.info("=== TEST 6: Consecutive Repeated Alert Triggers ===");
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        simulationService.startSimulation("mumbai-pune-expressway", 1);

        // Click Harsh Braking 4 times consecutively with distinct event IDs
        simulationService.triggerEvent("HARSH_BRAKING", null, "evt_hb_1");
        simulationService.triggerEvent("HARSH_BRAKING", null, "evt_hb_2");
        simulationService.triggerEvent("HARSH_BRAKING", null, "evt_hb_3");
        SimulationStateDTO s4 = simulationService.triggerEvent("HARSH_BRAKING", null, "evt_hb_4");

        // Verify 4 distinct alerts in session
        assertEquals(4, s4.getAllAlerts().size(), "Four distinct alert occurrences must be recorded in session");
        assertEquals(4, s4.getTotalAlertsCount());

        List<com.gps.tracking.dto.AlertDTO> alerts = s4.getAllAlerts();
        // Occurrence 1: Warning, ₹0
        assertEquals(1, alerts.get(0).getOccurrenceNumber());
        assertEquals(0, new BigDecimal("0.00").compareTo(alerts.get(0).getPenaltyAmount()));

        // Occurrence 2: Warning, ₹0
        assertEquals(2, alerts.get(1).getOccurrenceNumber());
        assertEquals(0, new BigDecimal("0.00").compareTo(alerts.get(1).getPenaltyAmount()));

        // Occurrence 3: Base fine (₹50)
        assertEquals(3, alerts.get(2).getOccurrenceNumber());
        assertEquals(0, new BigDecimal("50.00").compareTo(alerts.get(2).getPenaltyAmount()));

        // Occurrence 4: Base fine + increment (₹50 + ₹30 = ₹80)
        assertEquals(4, alerts.get(3).getOccurrenceNumber());
        assertEquals(0, new BigDecimal("80.00").compareTo(alerts.get(3).getPenaltyAmount()));

        // Total penalty amount = 0 + 0 + 50 + 80 = 130
        assertEquals(0, new BigDecimal("130.00").compareTo(s4.getTotalPenaltyAmount()));

        // Verify driver name is Justin Sunil across all alerts and state
        assertEquals(NotificationService.SIMULATION_DRIVER_NAME, s4.getDriverName());
        for (com.gps.tracking.dto.AlertDTO a : alerts) {
            assertEquals(NotificationService.SIMULATION_DRIVER_NAME, a.getDriverName());
            assertEquals(NotificationService.SIMULATION_DRIVER_EMAIL, a.getDriverEmail());
        }

        // Verify mailSender sent 4 alerts * 3 recipients = 12 emails total
        verify(mailSender, timeout(5000).times(12)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("TEST 7: Retrying identical event request with same eventId -> Idempotent, zero duplicate alerts or emails")
    public void test7_idempotentRetryDedup() {
        log.info("=== TEST 7: Idempotent Event Retry Deduplication ===");
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        simulationService.startSimulation("mumbai-pune-expressway", 1);

        String uniqueEventId = "evt_retry_test_101";
        SimulationStateDTO firstCall = simulationService.triggerEvent("HARSH_BRAKING", null, uniqueEventId);
        int initialAlertsCount = firstCall.getTotalAlertsCount();
        assertEquals(1, initialAlertsCount);

        // Network retry with exact same eventId
        SimulationStateDTO retryCall = simulationService.triggerEvent("HARSH_BRAKING", null, uniqueEventId);
        assertEquals(1, retryCall.getTotalAlertsCount(), "Retry must not create a second alert");

        // Exactly 3 emails sent (1 round for 3 recipients), not 6
        verify(mailSender, timeout(3000).times(3)).send(any(MimeMessage.class));
    }
}
