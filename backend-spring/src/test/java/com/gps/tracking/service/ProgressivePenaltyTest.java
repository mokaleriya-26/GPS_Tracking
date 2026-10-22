package com.gps.tracking.service;

import com.gps.tracking.dto.ChatResponseDTO;
import com.gps.tracking.dto.DriverPenaltySummaryDTO;
import com.gps.tracking.dto.FleetPenaltySummaryDTO;
import com.gps.tracking.entity.Alert;
import com.gps.tracking.entity.AlertPenaltyConfig;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.entity.DriverPenalty;
import com.gps.tracking.entity.Vehicle;
import com.gps.tracking.repository.AlertPenaltyConfigRepository;
import com.gps.tracking.repository.AlertRepository;
import com.gps.tracking.repository.DriverPenaltyRepository;
import com.gps.tracking.repository.DriverRepository;
import com.gps.tracking.repository.VehicleRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class ProgressivePenaltyTest {

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private PenaltyService penaltyService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private DriverPenaltyRepository driverPenaltyRepository;

    @Autowired
    private AlertPenaltyConfigRepository alertPenaltyConfigRepository;

    @Autowired
    private ChatService chatService;

    private Vehicle testVehicle;
    private Driver driverA;
    private Driver driverB;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        String u = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        testVehicle = vehicleRepository.save(Vehicle.builder()
                .code("VH_P_" + u)
                .registrationNumber("MH02" + u)
                .vehicleType("Van")
                .makeModel("Tata Ace")
                .active(true)
                .build());

        driverA = driverRepository.save(Driver.builder()
                .code("DRV_PA_" + System.currentTimeMillis())
                .name("Driver Alpha")
                .email("driver.alpha@example.com")
                .phone("9811100001")
                .active(true)
                .build());

        driverB = driverRepository.save(Driver.builder()
                .code("DRV_PB_" + System.currentTimeMillis())
                .name("Driver Beta")
                .email("driver.beta@example.com")
                .phone("9811100002")
                .active(true)
                .build());
    }

    private Alert createAlert(Driver driver, String alertType, String status, String deliveryStatus) {
        return alertRepository.save(Alert.builder()
                .reference("REF_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 5))
                .alertType(alertType)
                .severity("CRITICAL")
                .status(status != null ? status : "OPEN")
                .deliveryStatus(deliveryStatus != null ? deliveryStatus : "PENDING")
                .driver(driver)
                .vehicle(testVehicle)
                .occurredAt(LocalDateTime.now())
                .speedKmph(BigDecimal.valueOf(85.0))
                .message("Test Alert for " + alertType)
                .build());
    }

    @Test
    @DisplayName("TEST A: Harsh Braking Alert (Occurrences 1-5 -> ₹0, ₹0, ₹50, ₹80, ₹110)")
    void testA_harshBrakingOccurrences1To5() {
        // Occurrence 1: Warning, ₹0
        Alert a1 = createAlert(driverA, "Harsh Braking Alert", "OPEN", "PENDING");
        List<DriverPenalty> p1 = penaltyService.processAlertPenalty(a1);
        assertEquals(1, p1.size());
        assertEquals(1, p1.get(0).getOccurrenceNumber());
        assertEquals("WARNING", p1.get(0).getClassification());
        assertEquals(0, BigDecimal.ZERO.compareTo(p1.get(0).getMonetaryAmount()));

        // Occurrence 2: Warning, ₹0
        Alert a2 = createAlert(driverA, "Harsh Braking Alert", "OPEN", "PENDING");
        List<DriverPenalty> p2 = penaltyService.processAlertPenalty(a2);
        assertEquals(1, p2.size());
        assertEquals(2, p2.get(0).getOccurrenceNumber());
        assertEquals("WARNING", p2.get(0).getClassification());
        assertEquals(0, BigDecimal.ZERO.compareTo(p2.get(0).getMonetaryAmount()));

        // Occurrence 3: Warning + Base penalty (₹50)
        Alert a3 = createAlert(driverA, "Harsh Braking Alert", "OPEN", "PENDING");
        List<DriverPenalty> p3 = penaltyService.processAlertPenalty(a3);
        assertEquals(1, p3.size());
        assertEquals(3, p3.get(0).getOccurrenceNumber());
        assertEquals("PENALTY", p3.get(0).getClassification());
        assertEquals(0, new BigDecimal("50.00").compareTo(p3.get(0).getMonetaryAmount()));

        // Occurrence 4: Base + increment (₹80)
        Alert a4 = createAlert(driverA, "Harsh Braking Alert", "OPEN", "PENDING");
        List<DriverPenalty> p4 = penaltyService.processAlertPenalty(a4);
        assertEquals(1, p4.size());
        assertEquals(4, p4.get(0).getOccurrenceNumber());
        assertEquals("PENALTY", p4.get(0).getClassification());
        assertEquals(0, new BigDecimal("80.00").compareTo(p4.get(0).getMonetaryAmount()));

        // Occurrence 5: Base + 2 * increment (₹110)
        Alert a5 = createAlert(driverA, "Harsh Braking Alert", "OPEN", "PENDING");
        List<DriverPenalty> p5 = penaltyService.processAlertPenalty(a5);
        assertEquals(1, p5.size());
        assertEquals(5, p5.get(0).getOccurrenceNumber());
        assertEquals("PENALTY", p5.get(0).getClassification());
        assertEquals(0, new BigDecimal("110.00").compareTo(p5.get(0).getMonetaryAmount()));
    }

    @Test
    @DisplayName("TEST B: Over-speeding Alert (Occurrences 1-5 -> ₹0, ₹0, ₹100, ₹130, ₹160)")
    void testB_overspeedingOccurrences1To5() {
        // Occurrence 1: ₹0
        Alert a1 = createAlert(driverA, "Over-speeding Alert", "OPEN", "PENDING");
        List<DriverPenalty> p1 = penaltyService.processAlertPenalty(a1);
        assertEquals(1, p1.size());
        assertEquals(1, p1.get(0).getOccurrenceNumber());
        assertEquals("WARNING", p1.get(0).getClassification());
        assertEquals(0, BigDecimal.ZERO.compareTo(p1.get(0).getMonetaryAmount()));

        // Occurrence 2: ₹0
        Alert a2 = createAlert(driverA, "Over-speeding Alert", "OPEN", "PENDING");
        List<DriverPenalty> p2 = penaltyService.processAlertPenalty(a2);
        assertEquals(1, p2.size());
        assertEquals(2, p2.get(0).getOccurrenceNumber());
        assertEquals("WARNING", p2.get(0).getClassification());
        assertEquals(0, BigDecimal.ZERO.compareTo(p2.get(0).getMonetaryAmount()));

        // Occurrence 3: ₹100
        Alert a3 = createAlert(driverA, "Over-speeding Alert", "OPEN", "PENDING");
        List<DriverPenalty> p3 = penaltyService.processAlertPenalty(a3);
        assertEquals(1, p3.size());
        assertEquals(3, p3.get(0).getOccurrenceNumber());
        assertEquals("PENALTY", p3.get(0).getClassification());
        assertEquals(0, new BigDecimal("100.00").compareTo(p3.get(0).getMonetaryAmount()));

        // Occurrence 4: ₹130
        Alert a4 = createAlert(driverA, "Over-speeding Alert", "OPEN", "PENDING");
        List<DriverPenalty> p4 = penaltyService.processAlertPenalty(a4);
        assertEquals(1, p4.size());
        assertEquals(4, p4.get(0).getOccurrenceNumber());
        assertEquals("PENALTY", p4.get(0).getClassification());
        assertEquals(0, new BigDecimal("130.00").compareTo(p4.get(0).getMonetaryAmount()));

        // Occurrence 5: ₹160
        Alert a5 = createAlert(driverA, "Over-speeding Alert", "OPEN", "PENDING");
        List<DriverPenalty> p5 = penaltyService.processAlertPenalty(a5);
        assertEquals(1, p5.size());
        assertEquals(5, p5.get(0).getOccurrenceNumber());
        assertEquals("PENALTY", p5.get(0).getClassification());
        assertEquals(0, new BigDecimal("160.00").compareTo(p5.get(0).getMonetaryAmount()));
    }

    @Test
    @DisplayName("TEST C: Separate Alert Types (Harsh Braking does not increment Over-speeding)")
    void testC_separateAlertTypes() {
        // Driver A has 2 Harsh Braking alerts
        Alert hb1 = createAlert(driverA, "Harsh Braking Alert", "OPEN", "PENDING");
        penaltyService.processAlertPenalty(hb1);
        Alert hb2 = createAlert(driverA, "Harsh Braking Alert", "OPEN", "PENDING");
        penaltyService.processAlertPenalty(hb2);

        // Driver A has 1 Over-speeding alert
        Alert os1 = createAlert(driverA, "Over-speeding Alert", "OPEN", "PENDING");
        List<DriverPenalty> pList = penaltyService.processAlertPenalty(os1);

        assertEquals(1, pList.size());
        assertEquals(1, pList.get(0).getOccurrenceNumber(), "Over-speeding must be Occurrence 1, NOT 3");
        assertEquals("WARNING", pList.get(0).getClassification());
        assertEquals(0, BigDecimal.ZERO.compareTo(pList.get(0).getMonetaryAmount()));
    }

    @Test
    @DisplayName("TEST D: Different Drivers (Driver A violations do not increment Driver B)")
    void testD_differentDrivers() {
        // Driver A has 2 Over-speeding alerts
        Alert da1 = createAlert(driverA, "Over-speeding Alert", "OPEN", "PENDING");
        penaltyService.processAlertPenalty(da1);
        Alert da2 = createAlert(driverA, "Over-speeding Alert", "OPEN", "PENDING");
        penaltyService.processAlertPenalty(da2);

        // Driver B triggers Over-speeding alert
        Alert db1 = createAlert(driverB, "Over-speeding Alert", "OPEN", "PENDING");
        List<DriverPenalty> pList = penaltyService.processAlertPenalty(db1);

        assertEquals(1, pList.size());
        assertEquals(1, pList.get(0).getOccurrenceNumber(), "Driver B must receive Occurrence 1, NOT 3");
        assertEquals("WARNING", pList.get(0).getClassification());
        assertEquals(0, BigDecimal.ZERO.compareTo(pList.get(0).getMonetaryAmount()));
    }

    @Test
    @DisplayName("TEST E: Duplicate Processing (Idempotency -> no duplicate penalties)")
    void testE_duplicateProcessingIdempotent() {
        Alert a = createAlert(driverA, "Harsh Braking Alert", "OPEN", "PENDING");

        // First call
        List<DriverPenalty> firstRun = penaltyService.processAlertPenalty(a);
        assertEquals(1, firstRun.size());
        Long firstId = firstRun.get(0).getId();

        // Second duplicate call
        List<DriverPenalty> secondRun = penaltyService.processAlertPenalty(a);
        assertEquals(1, secondRun.size());
        assertEquals(firstId, secondRun.get(0).getId(), "Should return the existing penalty without duplicate insertion");

        // Total penalties for alert in repository should be exactly 1
        List<DriverPenalty> inDb = driverPenaltyRepository.findByAlertId(a.getId());
        assertEquals(1, inDb.size());
    }

    @Test
    @DisplayName("TEST F: Historical Import (historical/silenced alerts do not create penalties)")
    void testF_historicalImportNoPenalties() {
        // Alert flagged as HISTORICAL
        Alert histAlert = createAlert(driverA, "Harsh Braking Alert", "HISTORICAL", "PENDING");
        List<DriverPenalty> histResult = penaltyService.processAlertPenalty(histAlert);
        assertTrue(histResult.isEmpty(), "Historical alerts must not generate penalties");

        // Alert flagged as SILENCED
        Alert silencedAlert = createAlert(driverA, "Over-speeding Alert", "OPEN", "SILENCED");
        List<DriverPenalty> silencedResult = penaltyService.processAlertPenalty(silencedAlert);
        assertTrue(silencedResult.isEmpty(), "Silenced alerts must not generate penalties");

        // Verify nothing saved in driver_penalties table
        assertTrue(driverPenaltyRepository.findByAlertId(histAlert.getId()).isEmpty());
        assertTrue(driverPenaltyRepository.findByAlertId(silencedAlert.getId()).isEmpty());
    }

    @Test
    @DisplayName("TEST G: Application Restart / Re-evaluation (persisted amounts retained)")
    void testG_persistedPenaltiesRetained() {
        // Occurrence 1, 2, 3 for Driver B
        createAlert(driverB, "Night Driving Alert", "OPEN", "PENDING");
        penaltyService.processAlertPenalty(createAlert(driverB, "Night Driving Alert", "OPEN", "PENDING"));
        penaltyService.processAlertPenalty(createAlert(driverB, "Night Driving Alert", "OPEN", "PENDING"));
        Alert occ3 = createAlert(driverB, "Night Driving Alert", "OPEN", "PENDING");
        List<DriverPenalty> p3 = penaltyService.processAlertPenalty(occ3);

        assertEquals(1, p3.size());
        assertEquals(0, new BigDecimal("100.00").compareTo(p3.get(0).getMonetaryAmount()));

        // Simulate config change
        AlertPenaltyConfig config = alertPenaltyConfigRepository.findByAlertTypeIgnoreCase("Night Driving Alert").orElseThrow();
        BigDecimal originalFine = config.getDefaultFineAmount();
        try {
            config.setDefaultFineAmount(new BigDecimal("250.00"));
            alertPenaltyConfigRepository.save(config);

            // Re-evaluating existing alert returns existing persisted penalty with original ₹100 amount
            List<DriverPenalty> reevaluated = penaltyService.processAlertPenalty(occ3);
            assertEquals(1, reevaluated.size());
            assertEquals(0, new BigDecimal("100.00").compareTo(reevaluated.get(0).getMonetaryAmount()),
                    "Persisted penalty must retain historical ₹100, not recalculate to ₹250");
        } finally {
            config.setDefaultFineAmount(originalFine);
            alertPenaltyConfigRepository.save(config);
        }
    }

    @Test
    @DisplayName("TEST H: Concurrent Processing (two simultaneous alerts get sequential occurrence numbers)")
    void testH_concurrentProcessing() throws ExecutionException, InterruptedException {
        // Driver A already has 2 occurrences
        penaltyService.processAlertPenalty(createAlert(driverA, "Fatigue Alert", "OPEN", "PENDING"));
        penaltyService.processAlertPenalty(createAlert(driverA, "Fatigue Alert", "OPEN", "PENDING"));

        Alert alert1 = createAlert(driverA, "Fatigue Alert", "OPEN", "PENDING");
        Alert alert2 = createAlert(driverA, "Fatigue Alert", "OPEN", "PENDING");

        // Execute both concurrently
        CompletableFuture<List<DriverPenalty>> f1 = CompletableFuture.supplyAsync(() -> penaltyService.processAlertPenalty(alert1));
        CompletableFuture<List<DriverPenalty>> f2 = CompletableFuture.supplyAsync(() -> penaltyService.processAlertPenalty(alert2));

        CompletableFuture.allOf(f1, f2).get();

        List<DriverPenalty> res1 = f1.get();
        List<DriverPenalty> res2 = f2.get();

        assertEquals(1, res1.size());
        assertEquals(1, res2.size());

        int occ1 = res1.get(0).getOccurrenceNumber();
        int occ2 = res2.get(0).getOccurrenceNumber();

        assertTrue((occ1 == 3 && occ2 == 4) || (occ1 == 4 && occ2 == 3),
                "Concurrent alerts must get sequential distinct occurrences 3 and 4, got " + occ1 + " and " + occ2);
    }

    @Test
    @DisplayName("TEST I: Configuration Changes (new alerts use new base, old alerts keep old base)")
    void testI_configurationChangesApplyToFutureAlertsOnly() {
        AlertPenaltyConfig config = alertPenaltyConfigRepository.findByAlertTypeIgnoreCase("Harsh Acceleration Alert").orElseThrow();
        BigDecimal origFine = config.getDefaultFineAmount();
        try {
            // First 2 warnings
            penaltyService.processAlertPenalty(createAlert(driverA, "Harsh Acceleration Alert", "OPEN", "PENDING"));
            penaltyService.processAlertPenalty(createAlert(driverA, "Harsh Acceleration Alert", "OPEN", "PENDING"));

            // Occurrence 3 with original base ₹50
            Alert occ3 = createAlert(driverA, "Harsh Acceleration Alert", "OPEN", "PENDING");
            List<DriverPenalty> p3 = penaltyService.processAlertPenalty(occ3);
            assertEquals(0, new BigDecimal("50.00").compareTo(p3.get(0).getMonetaryAmount()));

            // Now manager changes base penalty to ₹75
            config.setDefaultFineAmount(new BigDecimal("75.00"));
            alertPenaltyConfigRepository.save(config);

            // Occurrence 4 uses new base ₹75 + increment ₹30 = ₹105
            Alert occ4 = createAlert(driverA, "Harsh Acceleration Alert", "OPEN", "PENDING");
            List<DriverPenalty> p4 = penaltyService.processAlertPenalty(occ4);
            assertEquals(4, p4.get(0).getOccurrenceNumber());
            assertEquals(0, new BigDecimal("105.00").compareTo(p4.get(0).getMonetaryAmount()));

            // Old occurrence 3 still has ₹50
            DriverPenalty reloadedP3 = driverPenaltyRepository.findById(p3.get(0).getId()).orElseThrow();
            assertEquals(0, new BigDecimal("50.00").compareTo(reloadedP3.getMonetaryAmount()));
        } finally {
            config.setDefaultFineAmount(origFine);
            alertPenaltyConfigRepository.save(config);
        }
    }

    @Test
    @DisplayName("TEST J: Notification Failure (alert is still penalized and not re-penalized on retry)")
    void testJ_notificationFailureDoesNotDuplicate() {
        // Driver B triggers 3rd overspeeding alert
        penaltyService.processAlertPenalty(createAlert(driverB, "Over-speeding Alert", "OPEN", "PENDING"));
        penaltyService.processAlertPenalty(createAlert(driverB, "Over-speeding Alert", "OPEN", "PENDING"));

        Alert a3 = createAlert(driverB, "Over-speeding Alert", "OPEN", "PENDING");

        // Process penalty successfully
        List<DriverPenalty> penalties = penaltyService.processAlertPenalty(a3);
        assertEquals(1, penalties.size());
        assertEquals("PENALTY", penalties.get(0).getClassification());

        // Simulate retry or notification dispatch failure
        List<DriverPenalty> retriedPenalties = penaltyService.processAlertPenalty(a3);
        assertEquals(1, retriedPenalties.size());
        assertEquals(penalties.get(0).getId(), retriedPenalties.get(0).getId(),
                "Retrying an alert must never create a duplicate penalty");
    }

    @Test
    @DisplayName("TEST K: Combined Alert Types (comma-separated types counted separately)")
    void testK_combinedAlertTypes() {
        // Combined alert containing Harsh Braking Alert and GPS Disconnect Alert
        Alert combined = createAlert(driverA, "Harsh Braking Alert, GPS Disconnect Alert", "OPEN", "PENDING");
        List<DriverPenalty> penalties = penaltyService.processAlertPenalty(combined);

        assertEquals(2, penalties.size(), "Combined alert must generate 2 separate penalty records");

        Optional<DriverPenalty> hbPenalty = penalties.stream()
                .filter(p -> "Harsh Braking Alert".equalsIgnoreCase(p.getAlertType()))
                .findFirst();
        Optional<DriverPenalty> gpsPenalty = penalties.stream()
                .filter(p -> "GPS Disconnect Alert".equalsIgnoreCase(p.getAlertType()))
                .findFirst();

        assertTrue(hbPenalty.isPresent(), "Harsh Braking penalty record must exist");
        assertTrue(gpsPenalty.isPresent(), "GPS Disconnect penalty record must exist");

        // GPS Disconnect base penalty is ₹0, so it remains ₹0 warning
        assertEquals(0, BigDecimal.ZERO.compareTo(gpsPenalty.get().getMonetaryAmount()));
    }

    @Test
    @DisplayName("TEST L: Reporting and Accounting (driver and fleet totals match individual penalties)")
    void testL_reportingAndAccounting() {
        Driver testDriverL = driverRepository.save(Driver.builder()
                .code("DRV_L_" + System.currentTimeMillis())
                .name("Driver Lima")
                .email("driver.lima@example.com")
                .phone("9811100099")
                .active(true)
                .build());

        // 2 warnings (₹0 each)
        penaltyService.processAlertPenalty(createAlert(testDriverL, "Over-speeding Alert", "OPEN", "PENDING"));
        penaltyService.processAlertPenalty(createAlert(testDriverL, "Over-speeding Alert", "OPEN", "PENDING"));

        // 3rd occurrence (₹100)
        penaltyService.processAlertPenalty(createAlert(testDriverL, "Over-speeding Alert", "OPEN", "PENDING"));
        // 4th occurrence (₹130)
        penaltyService.processAlertPenalty(createAlert(testDriverL, "Over-speeding Alert", "OPEN", "PENDING"));

        // Check Driver summary
        DriverPenaltySummaryDTO driverSummary = penaltyService.getDriverPenaltySummary(testDriverL.getId());
        assertEquals(4, driverSummary.getTotalQualifyingAlerts());
        assertEquals(2, driverSummary.getWarningOnlyCount());
        assertEquals(2, driverSummary.getPenaltyBearingCount());
        // ₹100 + ₹130 = ₹230
        assertEquals(0, new BigDecimal("230.00").compareTo(driverSummary.getTotalPenaltiesAccumulated()));

        // Check Fleet summary
        FleetPenaltySummaryDTO fleetSummary = penaltyService.getFleetPenaltySummary();
        assertNotNull(fleetSummary);
        assertTrue(fleetSummary.getTotalFleetPenalties().compareTo(new BigDecimal("230.00")) >= 0);
        assertTrue(fleetSummary.getTotalWarningOnlyAlerts() >= 2);
        assertTrue(fleetSummary.getTotalPenaltyBearingAlerts() >= 2);
    }

    @Test
    @DisplayName("TEST M: AI Chatbot answers penalty questions using PostgreSQL as ground truth")
    void testM_chatbotPenaltyQueriesAccurate() {
        String session = UUID.randomUUID().toString();

        // 1. Progressive penalty rules query
        ChatResponseDTO r1 = chatService.processMessage(session, "What are the progressive penalty rules?");
        assertEquals("PENALTY_QUERY", r1.getIntent());
        assertEquals("POSTGRESQL", r1.getSource());
        assertTrue(r1.getMessage().contains("Progressive Penalty Rules"));
        assertTrue(r1.getMessage().contains("Occurrence 1 & 2"));

        // 2. Specific alert type rule query
        ChatResponseDTO r2 = chatService.processMessage(session, "What is the penalty for a third harsh braking alert?");
        assertEquals("PENALTY_QUERY", r2.getIntent());
        assertEquals("POSTGRESQL", r2.getSource());
        assertTrue(r2.getMessage().contains("₹50") || r2.getMessage().contains("Harsh Braking"));

        // 3. Driver DRV009 penalty query
        ChatResponseDTO r3 = chatService.processMessage(session, "What is the total penalty amount for driver DRV009?");
        assertEquals("PENALTY_QUERY", r3.getIntent());
        assertEquals("POSTGRESQL", r3.getSource());
        assertTrue(r3.getMessage().contains("DRV009"));

        // 4. Zero-monetary penalties count
        ChatResponseDTO r4 = chatService.processMessage(session, "How many zero-monetary penalties have been issued?");
        assertEquals("PENALTY_QUERY", r4.getIntent());
        assertEquals("POSTGRESQL", r4.getSource());
        assertTrue(r4.getMessage().contains("warning") || r4.getMessage().contains("₹0"));

        // 5. Driver rankings query
        ChatResponseDTO r5 = chatService.processMessage(session, "List the top 5 penalized drivers.");
        assertEquals("PENALTY_QUERY", r5.getIntent());
        assertEquals("POSTGRESQL", r5.getSource());
        assertTrue(r5.getMessage().contains("Top Drivers by Cumulative Penalties") || r5.getMessage().contains("No driver penalty records"));
    }
}
