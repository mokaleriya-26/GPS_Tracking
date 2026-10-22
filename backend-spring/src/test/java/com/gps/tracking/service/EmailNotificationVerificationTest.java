package com.gps.tracking.service;

import com.gps.tracking.entity.Alert;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.entity.Vehicle;
import com.gps.tracking.repository.AlertRepository;
import com.gps.tracking.repository.DriverRepository;
import com.gps.tracking.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class EmailNotificationVerificationTest {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationVerificationTest.class);

    private static final String TEST_ALERT_REF = "TEST_ALERT_MERIN_JOYS_HARSH_BRAKING";
    private static final String TEST_DRIVER_NAME = "Merin Joys";
    private static final String TEST_DRIVER_EMAIL = "merinjoys28@gmail.com";
    private static final String TEST_DRIVER_PHONE = "8699881265";

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertService alertService;

    @Autowired
    private NotificationService notificationService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @org.junit.jupiter.api.BeforeEach
    public void setup() {
        jakarta.mail.internet.MimeMessage mimeMessage = new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null);
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    public void testCreateAlertAndSendDriverEmailNotification() {
        log.info("=== STEP 1: VERIFY OR CREATE TEST DRIVER ===");
        Optional<Driver> existingDriver = driverRepository.findByNameIgnoreCase(TEST_DRIVER_NAME);
        Driver driver;
        if (existingDriver.isPresent()) {
            driver = existingDriver.get();
            log.info("Found existing driver: ID={}, Code={}, Name={}, Phone={}",
                    driver.getId(), driver.getCode(), driver.getName(), driver.getPhone());
        } else {
            driver = driverRepository.save(Driver.builder()
                    .code("DRV089")
                    .name(TEST_DRIVER_NAME)
                    .phone(TEST_DRIVER_PHONE)
                    .active(true)
                    .build());
            log.info("Created new driver: ID={}, Code={}", driver.getId(), driver.getCode());
        }

        assertNotNull(driver.getId(), "Driver ID must not be null");
        assertEquals(TEST_DRIVER_NAME, driver.getName());

        log.info("=== STEP 2: PREPARE TEST ALERT ===");
        Vehicle vehicle = vehicleRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Vehicle ID 1 not found in database"));

        // Check if test alert already exists to prevent duplication
        Alert alert = alertRepository.findByReference(TEST_ALERT_REF).orElse(null);
        if (alert == null) {
            alert = Alert.builder()
                    .reference(TEST_ALERT_REF)
                    .alertType("Harsh Braking Alert")
                    .severity("WARNING")
                    .status("OPEN")
                    .vehicle(vehicle)
                    .driver(driver)
                    .occurredAt(LocalDateTime.now())
                    .speedKmph(BigDecimal.valueOf(42.50))
                    .speedLimitKmph(BigDecimal.valueOf(60.00))
                    .accelerationMps2(BigDecimal.valueOf(-3.8500))
                    .roadName("Western Express Highway, Mumbai")
                    .message("[TEST] Harsh Braking Alert for test driver Merin Joys (Email notification service verification)")
                    .deliveryStatus("PENDING")
                    .emailSent(false)
                    .smsSent(false)
                    .build();
            alert = alertService.saveAlert(alert);
            log.info("Created test alert: ID={}, Reference={}", alert.getId(), alert.getReference());
        } else {
            log.info("Reusing existing test alert: ID={}, Reference={}", alert.getId(), alert.getReference());
        }

        assertNotNull(alert.getId(), "Alert ID must not be null");
        assertEquals(driver.getId(), alert.getDriver().getId());

        log.info("=== STEP 3: EXECUTE REAL MULTI-RECIPIENT EMAIL NOTIFICATION ===");
        log.info("Dispatching to all 3 real recipients: FLEET, MANAGER, DRIVER");

        notificationService.sendEmailNotifications(alert, List.of("Harsh Braking Alert"));

        // Reload alert from DB to verify updated notification fields
        Alert updatedAlert = alertRepository.findById(alert.getId())
                .orElseThrow(() -> new IllegalStateException("Alert not found after notification"));

        log.info("=== STEP 4: VERIFY RESULTS ===");
        log.info("Delivery Status in DB: {}", updatedAlert.getDeliveryStatus());
        log.info("Fleet Email Sent: {}", updatedAlert.getFleetEmailSent());
        log.info("Manager Email Sent: {}", updatedAlert.getManagerEmailSent());
        log.info("Driver Email Sent: {}", updatedAlert.getDriverEmailSent());
        log.info("Email Sent At: {}", updatedAlert.getEmailSentAt());

        assertTrue(updatedAlert.getFleetEmailSent(), "Fleet email should have been sent");
        assertTrue(updatedAlert.getManagerEmailSent(), "Manager email should have been sent");
        assertTrue(updatedAlert.getDriverEmailSent(), "Driver email should have been sent");
        assertEquals("SENT", updatedAlert.getDeliveryStatus());
    }
}
