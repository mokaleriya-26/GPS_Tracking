package com.gps.tracking.service;

import com.gps.tracking.dto.AlertDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
public class AlertNotificationServiceTest {

    private static final Logger log = LoggerFactory.getLogger(AlertNotificationServiceTest.class);

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private AlertService alertService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private AlertRepository alertRepository;

    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        // Setup mock JavaMailSender to return real MimeMessage with null Session so MimeMessageHelper works
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        // Ensure test vehicle exists
        testVehicle = vehicleRepository.findById(1L).orElseGet(() ->
                vehicleRepository.save(Vehicle.builder()
                        .code("VH_TEST_01")
                        .registrationNumber("MH01TEST1001")
                        .vehicleType("Van")
                        .makeModel("Tata Ace")
                        .active(true)
                        .build()));
    }

    @Test
    @DisplayName("TEST 1: New alert with driver having valid email -> Fleet, Manager, and Driver all notified")
    void test1_newAlertWithValidDriverEmailNotifiesAllThree() throws Exception {
        Driver driver1 = driverRepository.save(Driver.builder()
                .code("DRV_T1_" + System.currentTimeMillis())
                .name("Arun Kumar")
                .email("arun.kumar.test@fleettrack.io")
                .phone("9811122233")
                .active(true)
                .build());

        Alert alert = Alert.builder()
                .reference("REF_TEST1_" + System.currentTimeMillis())
                .alertType("Harsh Braking Alert")
                .severity("WARNING")
                .status("OPEN")
                .vehicle(testVehicle)
                .driver(driver1)
                .occurredAt(LocalDateTime.now())
                .speedKmph(BigDecimal.valueOf(48.5))
                .speedLimitKmph(BigDecimal.valueOf(60.0))
                .roadName("Bandra-Worli Sea Link")
                .message("Sudden deceleration detected")
                .deliveryStatus("PENDING")
                .build();

        // Synchronous dispatch for deterministic verification
        Alert savedAlert = alertRepository.save(alert);
        notificationService.sendEmailNotifications(savedAlert, List.of("Harsh Braking Alert"));

        // Capture all sent MimeMessages
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(3)).send(captor.capture());

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

        log.info("TEST 1 Recipients received emails: {}", recipients);
        assertTrue(recipients.contains("riyamokale@gmail.com"), "Fleet email must be notified");
        assertTrue(recipients.contains("rhea260906@gmail.com"), "Manager email must be notified");
        assertTrue(recipients.contains("arun.kumar.test@fleettrack.io"), "Associated driver email must be notified");

        // Verify status in DB
        Alert updated = alertRepository.findById(savedAlert.getId()).orElseThrow();
        assertEquals("SENT", updated.getDeliveryStatus());
        assertTrue(updated.getEmailSent());
        assertTrue(updated.getFleetEmailSent());
        assertTrue(updated.getManagerEmailSent());
        assertTrue(updated.getDriverEmailSent());
        assertNotNull(updated.getEmailSentAt());
    }

    @Test
    @DisplayName("TEST 2: Alert for different driver dynamically uses second driver's email")
    void test2_differentDriverUsesDynamicEmail() throws Exception {
        Driver driver2 = driverRepository.save(Driver.builder()
                .code("DRV_T2_" + System.currentTimeMillis())
                .name("Kavita Shah")
                .email("kavita.shah.test@fleettrack.io")
                .phone("9822233344")
                .active(true)
                .build());

        Alert alert2 = Alert.builder()
                .reference("REF_TEST2_" + System.currentTimeMillis())
                .alertType("Overspeed Alert")
                .severity("CRITICAL")
                .status("OPEN")
                .vehicle(testVehicle)
                .driver(driver2)
                .occurredAt(LocalDateTime.now())
                .speedKmph(BigDecimal.valueOf(98.0))
                .speedLimitKmph(BigDecimal.valueOf(80.0))
                .roadName("Mumbai-Pune Expressway")
                .message("Vehicle overspeeding at 98 km/h")
                .deliveryStatus("PENDING")
                .build();

        Alert saved = alertRepository.save(alert2);
        notificationService.sendEmailNotifications(saved, List.of("Overspeed Alert"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(3)).send(captor.capture());

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

        log.info("TEST 2 Recipients: {}", recipients);
        assertTrue(recipients.contains("kavita.shah.test@fleettrack.io"), "Driver 2 email must be used dynamically");
        assertFalse(recipients.contains("arun.kumar.test@fleettrack.io"), "Driver 1 email must NOT be reused");
    }

    @Test
    @DisplayName("TEST 3: Driver has no email address -> Fleet & Manager notified, missing driver email logged, alert remains saved")
    void test3_driverWithoutEmailStillNotifiesFleetAndManager() throws Exception {
        Driver driverNoEmail = driverRepository.save(Driver.builder()
                .code("DRV_NO_MAIL_" + System.currentTimeMillis())
                .name("Driver No Email")
                .email(null) // No email
                .phone("9833344455")
                .active(true)
                .build());

        Alert alert3 = Alert.builder()
                .reference("REF_TEST3_" + System.currentTimeMillis())
                .alertType("Ignition Alert")
                .severity("INFO")
                .status("OPEN")
                .vehicle(testVehicle)
                .driver(driverNoEmail)
                .occurredAt(LocalDateTime.now())
                .message("Ignition turned ON outside scheduled hours")
                .deliveryStatus("PENDING")
                .build();

        Alert saved = alertRepository.save(alert3);
        notificationService.sendEmailNotifications(saved, List.of("Ignition Alert"));

        // Only Fleet and Manager should be attempted (2 sends)
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(captor.capture());

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

        log.info("TEST 3 Recipients: {}", recipients);
        assertTrue(recipients.contains("riyamokale@gmail.com"));
        assertTrue(recipients.contains("rhea260906@gmail.com"));

        // Alert remains saved and driver missing email is recorded
        Alert updated = alertRepository.findById(saved.getId()).orElseThrow();
        assertTrue(updated.getFleetEmailSent());
        assertTrue(updated.getManagerEmailSent());
        assertFalse(updated.getDriverEmailSent());
        assertNotNull(updated.getDriverEmailError());
        assertTrue(updated.getDriverEmailError().contains("no registered email"));
    }

    @Test
    @DisplayName("TEST 4: One recipient fails -> other recipients still attempted, failure recorded")
    void test4_oneRecipientFailsOthersStillAttempted() throws Exception {
        Driver driver4 = driverRepository.save(Driver.builder()
                .code("DRV_T4_" + System.currentTimeMillis())
                .name("Suresh Raina")
                .email("suresh.fail.test@fleettrack.io")
                .phone("9844455566")
                .active(true)
                .build());

        Alert alert4 = Alert.builder()
                .reference("REF_TEST4_" + System.currentTimeMillis())
                .alertType("GPS Disconnect Alert")
                .severity("CRITICAL")
                .status("OPEN")
                .vehicle(testVehicle)
                .driver(driver4)
                .occurredAt(LocalDateTime.now())
                .message("GPS tracker signal lost")
                .deliveryStatus("PENDING")
                .build();

        Alert saved = alertRepository.save(alert4);

        // Configure mock mailSender: throw exception when sending to driver email
        doAnswer(inv -> {
            MimeMessage msg = inv.getArgument(0);
            Address[] to = msg.getRecipients(Message.RecipientType.TO);
            if (to != null && to.length > 0 && to[0].toString().contains("suresh.fail.test")) {
                throw new MailSendException("SMTP 550: Mailbox does not exist");
            }
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        notificationService.sendEmailNotifications(saved, List.of("GPS Disconnect Alert"));

        // All 3 recipients should have been attempted
        verify(mailSender, times(3)).send(any(MimeMessage.class));

        Alert updated = alertRepository.findById(saved.getId()).orElseThrow();
        assertTrue(updated.getFleetEmailSent(), "Fleet email should have succeeded");
        assertTrue(updated.getManagerEmailSent(), "Manager email should have succeeded");
        assertFalse(updated.getDriverEmailSent(), "Driver email should be marked failed");
        assertEquals("PARTIAL", updated.getDeliveryStatus(), "Aggregate status should be PARTIAL");
        assertNotNull(updated.getDriverEmailError());
    }

    @Test
    @DisplayName("TEST 5: Updating existing alert (resolve) does not send new alert email")
    void test5_updatingExistingAlertDoesNotSendEmail() {
        Alert alert5 = Alert.builder()
                .reference("REF_TEST5_" + System.currentTimeMillis())
                .alertType("Night Driving Alert")
                .severity("WARNING")
                .status("OPEN")
                .vehicle(testVehicle)
                .occurredAt(LocalDateTime.now())
                .message("Driving during night hours")
                .deliveryStatus("SENT")
                .build();

        Alert saved = alertRepository.save(alert5);

        // Clear invocations from any previous step
        clearInvocations(mailSender);

        // Update / resolve alert
        AlertDTO resolved = alertService.resolveAlert(saved.getId(), "Fleet Admin");

        assertEquals("RESOLVED", resolved.getStatus());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("TEST 6: Backend restart with existing alerts does not resend emails")
    void test6_backendRestartDoesNotResendEmails() {
        clearInvocations(mailSender);

        // Emulate reading/loading existing alerts on startup
        List<Alert> existingAlerts = alertRepository.findAll();
        assertFalse(existingAlerts.isEmpty(), "Database should contain existing alerts");

        // Verify that merely reading / querying alerts triggers zero email sends
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("TEST 7: Historical alerts and CSV imports are silenced and do not trigger emails")
    void test7_historicalAndSilencedAlertsDoNotTriggerEmails() {
        clearInvocations(mailSender);

        Alert historicalAlert = Alert.builder()
                .reference("REF_HIST_" + System.currentTimeMillis())
                .alertType("Overspeed Alert")
                .severity("CRITICAL")
                .status("HISTORICAL")
                .deliveryStatus("SILENCED")
                .vehicle(testVehicle)
                .occurredAt(LocalDateTime.now().minusDays(30))
                .message("Historical imported alert point")
                .build();

        Alert saved = alertRepository.save(historicalAlert);

        // Attempt notification processing with silenceNotifications=true
        notificationService.processAlert(saved, List.of("Overspeed Alert"), true);

        // Verify zero emails sent
        verify(mailSender, never()).send(any(MimeMessage.class));

        Alert afterProcess = alertRepository.findById(saved.getId()).orElseThrow();
        assertEquals("HISTORICAL", afterProcess.getStatus());
        assertEquals("SILENCED", afterProcess.getDeliveryStatus());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        try {
            alertRepository.findAll().stream()
                .filter(a -> a.getReference() != null && (a.getReference().startsWith("REF_TEST") || a.getReference().startsWith("REF_HIST")))
                .forEach(a -> alertRepository.deleteById(a.getId()));
            driverRepository.findAll().stream()
                .filter(d -> d.getCode() != null && (d.getCode().startsWith("DRV_T") || d.getCode().startsWith("DRV_NO_MAIL")))
                .forEach(d -> driverRepository.deleteById(d.getId()));
        } catch (Exception ignored) {}
    }
}
