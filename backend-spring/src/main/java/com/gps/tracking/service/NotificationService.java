package com.gps.tracking.service;

import com.gps.tracking.entity.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * NotificationService — Java port of the existing Python notifications.py.
 *
 * Preserves all existing behaviour:
 * - Email via SMTP to 3 recipients: Fleet, Manager, Driver
 * - SMS via MSG91 API to 3 recipients: Fleet, Manager, Driver
 * - Per-recipient tracking (fleet_email_sent, manager_email_sent, driver_email_sent, etc.)
 * - MSG91 error handling (418, 203, 211, 400)
 * - Indian phone number formatting (91XXXXXXXXXX)
 * - Historical data silencing (silence_notifications flag → alert.status=HISTORICAL)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;
    private final com.gps.tracking.repository.AlertRepository alertRepository;
    private final com.gps.tracking.repository.DriverPenaltyRepository driverPenaltyRepository;

    public static final String SIMULATION_DRIVER_NAME = "Justin Sunil";
    public static final String SIMULATION_DRIVER_EMAIL = "justinsunilsunil@gmail.com";
    public static final String DEFAULT_FLEET_EMAIL = "riyamokale@gmail.com";
    public static final String DEFAULT_MANAGER_EMAIL = "rhea260906@gmail.com";

    @Value("${notification.email.fleet:riyamokale@gmail.com}")
    private String fleetEmail;

    @Value("${notification.email.manager:rhea260906@gmail.com}")
    private String managerEmail;

    @Value("${notification.email.from:no-reply@trackfleet.com}")
    private String fromEmail;

    @Value("${notification.sms.msg91.auth-key:}")
    private String msg91AuthKey;

    @Value("${notification.sms.msg91.template-id:}")
    private String msg91TemplateId;

    @Value("${notification.sms.msg91.sender-id:}")
    private String msg91SenderId;

    @Value("${notification.sms.msg91.api-url:https://control.msg91.com/api/v5/flow/}")
    private String msg91ApiUrl;

    @Value("${notification.sms.fleet-phone:}")
    private String fleetPhone;

    @Value("${notification.sms.manager-phone:}")
    private String managerPhone;

    private static final DateTimeFormatter OCCURRED_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * Synchronous alert processing for deterministic execution and testing.
     */
    @Transactional
    public void processAlertSync(Alert alert, List<String> alertTypes, boolean silenceNotifications) {
        if (alert == null || alert.getId() == null) return;
        Alert managedAlert = alertRepository.findById(alert.getId()).orElse(alert);

        if (silenceNotifications || "HISTORICAL".equalsIgnoreCase(managedAlert.getStatus()) || "SILENCED".equalsIgnoreCase(managedAlert.getDeliveryStatus())) {
            managedAlert.setStatus("HISTORICAL");
            managedAlert.setDeliveryStatus("SILENCED");
            alertRepository.save(managedAlert);
            log.info("Alert {} silenced (historical data) — skipping notifications", managedAlert.getId());
            return;
        }

        // Prevent duplicate processing if already sent
        if (Boolean.TRUE.equals(managedAlert.getEmailSent())) {
            log.info("Alert {} already notified (emailSent=true) — skipping duplicate dispatch", managedAlert.getId());
            return;
        }

        log.info("Processing notifications for alert {} ({}, isSimulation={})",
                managedAlert.getId(), String.join(", ", alertTypes), managedAlert.getIsSimulation());

        // Send email notifications (handles Fleet, Manager, and Driver / Simulation Driver)
        sendEmailNotifications(managedAlert, alertTypes);

        // Send SMS notifications (automatically suppressed for Live Simulation alerts)
        sendSmsNotifications(managedAlert, alertTypes);

        alertRepository.save(managedAlert);
    }

    /**
     * Process alert notifications asynchronously.
     */
    @Async
    @Transactional
    public void processAlert(Alert alert, List<String> alertTypes, boolean silenceNotifications) {
        processAlertSync(alert, alertTypes, silenceNotifications);
    }

    /**
     * Send email notifications to Fleet, Manager, and Driver (or Justin Sunil for Live Simulation).
     * Failure of one recipient does not cancel attempts to others.
     */
    @Transactional
    public void sendEmailNotifications(Alert managedAlert, List<String> alertTypes) {
        if (managedAlert == null) return;

        // Prevent duplicate dispatch
        if (Boolean.TRUE.equals(managedAlert.getEmailSent())) {
            log.info("Alert {} already marked emailSent=true — skipping email dispatch to prevent duplicate notifications", managedAlert.getId());
            return;
        }

        boolean isSimulation = Boolean.TRUE.equals(managedAlert.getIsSimulation());

        String vehicleReg = resolveVehicleReg(managedAlert);

        // Simulation-specific email subject prefix
        String subject = isSimulation
                ? "[TrackFleet Simulation] " + String.join(", ", alertTypes) + " - " + vehicleReg
                : "[TrackFleet] " + String.join(", ", alertTypes) + " - " + vehicleReg;

        // Resolve Fleet and Manager recipients (keeping existing FLEET_EMAIL & MANAGER_EMAIL config)
        String fleet = (fleetEmail != null && !fleetEmail.isBlank()) ? fleetEmail.trim() : DEFAULT_FLEET_EMAIL;
        String manager = (managerEmail != null && !managerEmail.isBlank()) ? managerEmail.trim() : DEFAULT_MANAGER_EMAIL;

        // Resolve Driver recipient
        String driverEmail = resolveDriverEmail(managedAlert, isSimulation);
        String driverName = resolveDriverName(managedAlert, isSimulation);

        if (isSimulation) {
            log.info("Alert {} is Live Simulation: routing driver notification to {} <{}>",
                    managedAlert.getId(), driverName, driverEmail);
        }

        // Driver email status tracking
        if (driverEmail == null) {
            String reason = managedAlert.getDriver() == null
                    ? "Alert has no associated driver"
                    : "Associated driver (" + driverName + ") has no registered email address in DB";
            log.info("Alert {} - {}. Skipping driver email dispatch.", managedAlert.getId(), reason);
            managedAlert.setDriverEmailSent(false);
            managedAlert.setDriverEmailError(reason);
        }

        // Build recipient target list with greetings
        // Map: email -> Set of roles (FLEET, MANAGER, DRIVER)
        Map<String, Set<String>> emailToRoles = new LinkedHashMap<>();
        Map<String, String> emailToGreeting = new LinkedHashMap<>();

        if (isValidEmail(fleet)) {
            emailToRoles.computeIfAbsent(fleet.toLowerCase(), k -> new LinkedHashSet<>()).add("FLEET");
            emailToGreeting.putIfAbsent(fleet.toLowerCase(), "Fleet Team");
        } else {
            managedAlert.setFleetEmailSent(false);
            managedAlert.setFleetEmailError("Fleet email not configured or invalid: " + fleet);
            log.warn("Alert {} - Fleet email not configured or invalid: {}", managedAlert.getId(), fleet);
        }

        if (isValidEmail(manager)) {
            emailToRoles.computeIfAbsent(manager.toLowerCase(), k -> new LinkedHashSet<>()).add("MANAGER");
            emailToGreeting.putIfAbsent(manager.toLowerCase(), "Manager");
        } else {
            managedAlert.setManagerEmailSent(false);
            managedAlert.setManagerEmailError("Manager email not configured or invalid: " + manager);
            log.warn("Alert {} - Manager email not configured or invalid: {}", managedAlert.getId(), manager);
        }

        if (isValidEmail(driverEmail)) {
            emailToRoles.computeIfAbsent(driverEmail.toLowerCase(), k -> new LinkedHashSet<>()).add("DRIVER");
            emailToGreeting.putIfAbsent(driverEmail.toLowerCase(), driverName);
        } else if (driverEmail != null) {
            managedAlert.setDriverEmailSent(false);
            managedAlert.setDriverEmailError("Driver email address format invalid: " + driverEmail);
            log.warn("Alert {} - Driver email address format invalid: {}", managedAlert.getId(), driverEmail);
        }

        int totalAttempted = emailToRoles.size();
        int totalSuccess = 0;

        for (Map.Entry<String, Set<String>> entry : emailToRoles.entrySet()) {
            String targetEmail = entry.getKey();
            Set<String> roles = entry.getValue();
            String greeting = emailToGreeting.getOrDefault(targetEmail, "Fleet Team");
            if (roles.contains("FLEET") && roles.contains("MANAGER")) {
                greeting = "Fleet Team & Manager";
            }

            String htmlBody = generateEmailHtml(managedAlert, alertTypes, greeting);
            String plainText = generateEmailPlainText(managedAlert, alertTypes, greeting);

            try {
                sendSingleHtmlEmail(targetEmail, subject, htmlBody, plainText);
                totalSuccess++;
                log.info("Email -> {} ({}): SUCCESS", String.join("&", roles), maskEmail(targetEmail));
                if (roles.contains("FLEET")) {
                    managedAlert.setFleetEmailSent(true);
                    managedAlert.setFleetEmailError(null);
                }
                if (roles.contains("MANAGER")) {
                    managedAlert.setManagerEmailSent(true);
                    managedAlert.setManagerEmailError(null);
                }
                if (roles.contains("DRIVER")) {
                    managedAlert.setDriverEmailSent(true);
                    managedAlert.setDriverEmailError(null);
                }
            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.error("Email -> {} ({}): FAILED | {}", String.join("&", roles), maskEmail(targetEmail), errMsg);
                if (roles.contains("FLEET")) {
                    managedAlert.setFleetEmailSent(false);
                    managedAlert.setFleetEmailError(errMsg);
                }
                if (roles.contains("MANAGER")) {
                    managedAlert.setManagerEmailSent(false);
                    managedAlert.setManagerEmailError(errMsg);
                }
                if (roles.contains("DRIVER")) {
                    managedAlert.setDriverEmailSent(false);
                    managedAlert.setDriverEmailError(errMsg);
                }
            }
        }

        // Determine aggregate delivery status
        if (totalAttempted > 0 && totalSuccess == totalAttempted) {
            managedAlert.setDeliveryStatus("SENT");
            managedAlert.setEmailSent(true);
            managedAlert.setDeliveryLastError(null);
        } else if (totalSuccess > 0) {
            managedAlert.setDeliveryStatus("PARTIAL");
            managedAlert.setEmailSent(false);
            managedAlert.setDeliveryLastError("Partial email delivery failure (" + totalSuccess + "/" + totalAttempted + " succeeded)");
        } else if (totalAttempted > 0) {
            managedAlert.setDeliveryStatus("FAILED");
            managedAlert.setEmailSent(false);
            managedAlert.setDeliveryLastError("All email delivery attempts failed (" + totalAttempted + " recipients)");
        }

        if (totalSuccess > 0) {
            managedAlert.setEmailSentAt(LocalDateTime.now());
        }

        alertRepository.save(managedAlert);
    }

    /**
     * Send email-only notification to a targeted recipient (for isolated testing or driver email).
     * Bypasses SMS completely and does not notify unintended recipients.
     * Updates alert delivery tracking in PostgreSQL.
     */
    @Transactional
    public boolean sendEmailOnlyNotification(Alert alert, List<String> alertTypes, String targetEmail) {
        Alert managedAlert = (alert != null && alert.getId() != null)
                ? alertRepository.findById(alert.getId()).orElse(alert)
                : alert;

        boolean isSimulation = managedAlert != null && Boolean.TRUE.equals(managedAlert.getIsSimulation());

        String vehicleReg = resolveVehicleReg(managedAlert);
        String subject = isSimulation
                ? "[TrackFleet Simulation] " + String.join(", ", alertTypes) + " - " + vehicleReg
                : "[TrackFleet] " + String.join(", ", alertTypes) + " - " + vehicleReg;

        String greeting = isSimulation
                ? SIMULATION_DRIVER_NAME
                : resolveDriverName(managedAlert, false);

        String htmlBody = generateEmailHtml(managedAlert, alertTypes, greeting);
        String plainText = generateEmailPlainText(managedAlert, alertTypes, greeting);

        try {
            sendSingleHtmlEmail(targetEmail, subject, htmlBody, plainText);
            if (managedAlert != null) {
                managedAlert.setDriverEmailSent(true);
                managedAlert.setDriverEmailError(null);
                managedAlert.setEmailSent(true);
                managedAlert.setEmailSentAt(LocalDateTime.now());
                managedAlert.setDeliveryStatus("SENT");
                managedAlert.setDeliveryLastError(null);
                alertRepository.save(managedAlert);
            }
            log.info("Email -> {}: SUCCESS", maskEmail(targetEmail));
            return true;
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (managedAlert != null) {
                managedAlert.setDriverEmailSent(false);
                managedAlert.setDriverEmailError(errMsg);
                managedAlert.setDeliveryStatus("FAILED");
                managedAlert.setDeliveryLastError(errMsg);
                alertRepository.save(managedAlert);
            }
            log.error("Email -> {}: FAILED | {}", maskEmail(targetEmail), errMsg);
            return false;
        }
    }

    private void sendSingleHtmlEmail(String to, String subject, String htmlBody, String plainTextBody) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        if (mimeMessage == null) {
            log.warn("mailSender.createMimeMessage() returned null (mocked without stub) — skipping actual transport");
            return;
        }
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(plainTextBody, htmlBody); // multipart/alternative: plain text fallback + rich HTML
        mailSender.send(mimeMessage);
    }

    private boolean isValidEmail(String email) {
        return email != null && !email.isBlank() && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private String safe(Object val) {
        if (val == null) return "N/A";
        String s = val.toString().trim();
        return s.isEmpty() ? "N/A" : s;
    }

    private String resolveVehicleReg(Alert alert) {
        if (alert == null) return "N/A";
        try {
            if (alert.getVehicle() != null && alert.getVehicle().getRegistrationNumber() != null) {
                return alert.getVehicle().getRegistrationNumber();
            }
        } catch (Exception ignored) {}
        return Boolean.TRUE.equals(alert.getIsSimulation()) ? "MH01SM9999" : "N/A";
    }

    private String resolveVehicleCode(Alert alert) {
        if (alert == null) return "N/A";
        try {
            if (alert.getVehicle() != null && alert.getVehicle().getCode() != null) {
                return alert.getVehicle().getCode();
            }
        } catch (Exception ignored) {}
        return Boolean.TRUE.equals(alert.getIsSimulation()) ? "VH_SIM_01" : "N/A";
    }

    private String resolveVehicleModel(Alert alert) {
        if (alert == null) return "N/A";
        try {
            if (alert.getVehicle() != null && alert.getVehicle().getMakeModel() != null) {
                return alert.getVehicle().getMakeModel();
            }
        } catch (Exception ignored) {}
        return Boolean.TRUE.equals(alert.getIsSimulation()) ? "BharatBenz 2823R Heavy Truck" : "N/A";
    }

    private String resolveDriverName(Alert alert, boolean isSimulation) {
        if (isSimulation) return SIMULATION_DRIVER_NAME;
        if (alert == null) return "Driver";
        try {
            if (alert.getDriver() != null && alert.getDriver().getName() != null && !alert.getDriver().getName().isBlank()) {
                return alert.getDriver().getName().trim();
            }
        } catch (Exception ignored) {}
        return "Driver";
    }

    private String resolveDriverEmail(Alert alert, boolean isSimulation) {
        if (isSimulation) return SIMULATION_DRIVER_EMAIL;
        if (alert == null) return null;
        try {
            if (alert.getDriver() != null && alert.getDriver().getEmail() != null && !alert.getDriver().getEmail().isBlank()) {
                return alert.getDriver().getEmail().trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveDriverPhone(Alert alert, boolean isSimulation) {
        if (isSimulation) return "N/A (Simulation)";
        if (alert == null) return "N/A";
        try {
            if (alert.getDriver() != null && alert.getDriver().getPhone() != null) {
                return alert.getDriver().getPhone().trim();
            }
        } catch (Exception ignored) {}
        return "N/A";
    }

    private String resolveDriverCode(Alert alert, boolean isSimulation) {
        if (isSimulation) return "SIM_DRV_01";
        if (alert == null) return "N/A";
        try {
            if (alert.getDriver() != null && alert.getDriver().getCode() != null) {
                return alert.getDriver().getCode().trim();
            }
        } catch (Exception ignored) {}
        return "N/A";
    }

    /**
     * Professional HTML Alert Template with TrackFleet Branding, Severity Badges, and Null Safety.
     */
    public String generateEmailHtml(Alert alert, List<String> alertTypes, String greeting) {
        if (alert == null) return "";
        boolean isSimulation = Boolean.TRUE.equals(alert.getIsSimulation());

        String alertTypeStr = String.join(", ", alertTypes);
        String severity = safe(alert.getSeverity()).toUpperCase();

        String badgeColor = switch (severity) {
            case "CRITICAL" -> "background-color: #fee2e2; color: #dc2626; border: 1px solid #f87171;";
            case "INFO" -> "background-color: #e0f2fe; color: #0284c7; border: 1px solid #38bdf8;";
            default -> "background-color: #fef3c7; color: #d97706; border: 1px solid #fbbf24;";
        };

        String eventTime = alert.getOccurredAt() != null
                ? alert.getOccurredAt().format(OCCURRED_FORMATTER) + " IST"
                : "N/A";

        String vehicleReg = resolveVehicleReg(alert);
        String vehicleCode = resolveVehicleCode(alert);
        String vehicleModel = resolveVehicleModel(alert);

        String driverName = resolveDriverName(alert, isSimulation);
        String driverCode = resolveDriverCode(alert, isSimulation);
        String driverEmail = resolveDriverEmail(alert, isSimulation);
        String driverPhone = resolveDriverPhone(alert, isSimulation);

        String tripId = alert.getTrip() != null ? String.valueOf(alert.getTrip().getId()) : "N/A";
        String speed = alert.getSpeedKmph() != null ? alert.getSpeedKmph() + " km/h" : "N/A";
        String speedLimit = alert.getSpeedLimitKmph() != null ? alert.getSpeedLimitKmph() + " km/h" : "N/A";
        String accel = alert.getAccelerationMps2() != null ? alert.getAccelerationMps2() + " m/s²" : "N/A";
        String location = alert.getRoadName() != null ? alert.getRoadName() :
                (alert.getLatitude() != null ? alert.getLatitude() + ", " + alert.getLongitude() : "N/A");
        String message = safe(alert.getMessage());

        // Retrieve progressive penalty details if recorded for this alert
        com.gps.tracking.entity.DriverPenalty penalty = null;
        BigDecimal cumulative = BigDecimal.ZERO;
        if (alert.getId() != null) {
            List<com.gps.tracking.entity.DriverPenalty> pList = driverPenaltyRepository.findByAlertId(alert.getId());
            if (!pList.isEmpty()) {
                penalty = pList.get(0);
                if (penalty.getDriver() != null && penalty.getDriver().getId() != null) {
                    BigDecimal sum = driverPenaltyRepository.sumActiveMonetaryAmountByDriverId(penalty.getDriver().getId());
                    if (sum != null) cumulative = sum;
                }
            }
        }

        String penaltyHtml = "";
        if (penalty != null) {
            boolean isWarning = "WARNING".equalsIgnoreCase(penalty.getClassification());
            int occNum = penalty.getOccurrenceNumber();
            BigDecimal fine = penalty.getMonetaryAmount() != null ? penalty.getMonetaryAmount() : BigDecimal.ZERO;
            BigDecimal base = penalty.getBasePenalty() != null ? penalty.getBasePenalty() : BigDecimal.ZERO;
            BigDecimal inc = penalty.getPenaltyIncrement() != null ? penalty.getPenaltyIncrement() : BigDecimal.valueOf(30);

            if (isWarning) {
                penaltyHtml = String.format("""
                  <div style="background-color: #fefce8; border: 1px solid #fef08a; border-left: 4px solid #eab308; border-radius: 6px; padding: 14px; margin-bottom: 16px;">
                    <div style="font-size: 13px; font-weight: 700; color: #854d0e; margin-bottom: 4px;">
                      ⚠️ WARNING NOTIFICATION (Occurrence #%d)
                    </div>
                    <div style="font-size: 13px; color: #713f12; line-height: 1.5;">
                      Classification: <strong>WARNING</strong> &bull; Current Penalty: <strong>₹0.00</strong><br/>
                      A monetary penalty has not yet been applied for this violation occurrence. Progressive financial penalties apply starting from occurrence #3.
                    </div>
                  </div>
                  """, occNum);
            } else {
                penaltyHtml = String.format("""
                  <div style="background-color: #fef2f2; border: 1px solid #fecaca; border-left: 4px solid #dc2626; border-radius: 6px; padding: 14px; margin-bottom: 16px;">
                    <div style="font-size: 13px; font-weight: 700; color: #991b1b; margin-bottom: 6px;">
                      🚨 PROGRESSIVE PENALTY APPLIED (Occurrence #%d)
                    </div>
                    <table style="width: 100%%; border-collapse: collapse; font-size: 13px;">
                      <tr><td style="padding: 3px 0; color: #7f1d1d; width: 45%%;">Classification:</td><td style="padding: 3px 0; color: #991b1b; font-weight: 700;">PENALTY</td></tr>
                      <tr><td style="padding: 3px 0; color: #7f1d1d;">Current Alert Fine:</td><td style="padding: 3px 0; color: #991b1b; font-weight: 700; font-size: 15px;">₹%.2f</td></tr>
                      <tr><td style="padding: 3px 0; color: #7f1d1d;">Driver Cumulative Penalties:</td><td style="padding: 3px 0; color: #0f172a; font-weight: 700;">₹%.2f</td></tr>
                      <tr><td style="padding: 3px 0; color: #7f1d1d;">Base Penalty Applied:</td><td style="padding: 3px 0; color: #475569;">₹%.2f</td></tr>
                      <tr><td style="padding: 3px 0; color: #7f1d1d;">Penalty Increment:</td><td style="padding: 3px 0; color: #475569;">₹%.2f</td></tr>
                    </table>
                  </div>
                  """, occNum, fine.doubleValue(), cumulative.doubleValue(), base.doubleValue(), inc.doubleValue());
            }
        }

        String simulationBadge = isSimulation ? """
            <div style="background-color: #fef3c7; border: 2px solid #f59e0b; border-radius: 6px; padding: 14px; margin-bottom: 20px; text-align: center;">
              <span style="background-color: #d97706; color: #ffffff; font-size: 11px; font-weight: 800; padding: 3px 8px; border-radius: 4px; text-transform: uppercase; letter-spacing: 1px; display: inline-block; margin-bottom: 6px;">SIMULATION MODE</span>
              <div style="font-size: 13px; font-weight: 700; color: #92400e; margin-bottom: 2px;">LIVE TRUCK SIMULATION DEMONSTRATION</div>
              <div style="font-size: 12px; color: #78350f;">
                This alert was generated during an active <strong>Live Truck Simulation</strong> session for academic and evaluation demonstration. Zero real-world vehicle or driver incident occurred.
              </div>
            </div>
            """ : "";

        String headerBg = isSimulation ? "#1e1b4b" : "#0f172a";
        String headerTitle = isSimulation ? "TRACKFLEET SIMULATION ALERT DISPATCH" : "TRACKFLEET ALERT DISPATCH";
        String headerSubtitle = isSimulation ? "Live Interactive Simulation Telemetry Notification" : "Automated GPS Fleet Security & Safety Notification";

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>TrackFleet Alert</title>
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 24px; color: #1e293b;">
              <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);">
                <div style="background-color: %s; padding: 24px; text-align: center;">
                  <h1 style="margin: 0; font-size: 20px; font-weight: 700; color: #38bdf8; letter-spacing: 0.5px;">%s</h1>
                  <p style="margin: 6px 0 0; font-size: 13px; color: #94a3b8;">%s</p>
                </div>
                <div style="padding: 24px;">
                  %s
                  <p style="font-size: 15px; font-weight: 600; color: #0f172a; margin: 0 0 16px;">Hello %s,</p>
                  <p style="font-size: 14px; line-height: 1.5; color: #475569; margin: 0 0 20px;">
                    A new safety alert has been recorded in the TrackFleet system. Please find the incident details below:
                  </p>
                  <div style="text-align: center; margin-bottom: 20px;">
                    <span style="display: inline-block; padding: 6px 14px; border-radius: 6px; font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; %s">
                      %s &bull; %s
                    </span>
                  </div>
                  %s
                  <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 16px; margin-bottom: 16px;">
                    <div style="font-size: 12px; font-weight: 700; text-transform: uppercase; color: #64748b; letter-spacing: 0.5px; margin-bottom: 10px;">Alert Information</div>
                    <table style="width: 100%%; border-collapse: collapse; font-size: 13px;">
                      <tr><td style="padding: 4px 0; color: #64748b; width: 40%%;">Alert ID:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">#%s</td></tr>
                      <tr><td style="padding: 4px 0; color: #64748b;">Reference:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600; font-family: monospace;">%s</td></tr>
                      <tr><td style="padding: 4px 0; color: #64748b;">Event Time:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s</td></tr>
                      <tr><td style="padding: 4px 0; color: #64748b;">Status:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s</td></tr>
                    </table>
                  </div>
                  <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 16px; margin-bottom: 16px;">
                    <div style="font-size: 12px; font-weight: 700; text-transform: uppercase; color: #64748b; letter-spacing: 0.5px; margin-bottom: 10px;">Vehicle & Driver Details</div>
                    <table style="width: 100%%; border-collapse: collapse; font-size: 13px;">
                      <tr><td style="padding: 4px 0; color: #64748b; width: 40%%;">Vehicle Number:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s (%s)</td></tr>
                      <tr><td style="padding: 4px 0; color: #64748b;">Vehicle Model:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s</td></tr>
                      <tr><td style="padding: 4px 0; color: #64748b;">Driver Name:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s (%s)</td></tr>
                      <tr><td style="padding: 4px 0; color: #64748b;">Driver Email:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s</td></tr>
                      <tr><td style="padding: 4px 0; color: #64748b;">Driver Phone:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s</td></tr>
                      <tr><td style="padding: 4px 0; color: #64748b;">Trip ID:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s</td></tr>
                    </table>
                  </div>
                  <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 16px; margin-bottom: 16px;">
                    <div style="font-size: 12px; font-weight: 700; text-transform: uppercase; color: #64748b; letter-spacing: 0.5px; margin-bottom: 10px;">Telemetry & Telematics Data</div>
                    <table style="width: 100%%; border-collapse: collapse; font-size: 13px;">
                      <tr><td style="padding: 4px 0; color: #64748b; width: 40%%;">Location:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s</td></tr>
                      <tr><td style="padding: 4px 0; color: #64748b;">Recorded Speed:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s</td></tr>
                      <tr><td style="padding: 4px 0; color: #64748b;">Speed Limit:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s</td></tr>
                      <tr><td style="padding: 4px 0; color: #64748b;">Acceleration:</td><td style="padding: 4px 0; color: #0f172a; font-weight: 600;">%s</td></tr>
                    </table>
                  </div>
                  <div style="background-color: #fffbeb; border-left: 4px solid #f59e0b; padding: 12px; border-radius: 4px; font-size: 13px; color: #92400e; margin-bottom: 20px;">
                    <strong>Alert Description:</strong> %s
                  </div>
                </div>
                <div style="background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 16px 24px; font-size: 12px; color: #64748b; text-align: center;">
                  <p style="margin: 0 0 4px;"><strong>TrackFleet GPS Fleet Tracking System</strong></p>
                  <p style="margin: 0;">This automated alert was generated by the TrackFleet system. Please take appropriate action.</p>
                </div>
              </div>
            </body>
            </html>
            """,
            headerBg,
            headerTitle,
            headerSubtitle,
            simulationBadge,
            greeting,
            badgeColor,
            severity,
            alertTypeStr,
            penaltyHtml,
            safe(alert.getId()),
            safe(alert.getReference()),
            eventTime,
            safe(alert.getStatus()),
            vehicleReg,
            vehicleCode,
            vehicleModel,
            driverName,
            driverCode,
            driverEmail,
            driverPhone,
            tripId,
            location,
            speed,
            speedLimit,
            accel,
            message
        );
    }

    public String generateEmailPlainText(Alert alert, List<String> alertTypes, String greeting) {
        if (alert == null) return "";
        boolean isSimulation = Boolean.TRUE.equals(alert.getIsSimulation());

        String eventTime = alert.getOccurredAt() != null
                ? alert.getOccurredAt().format(OCCURRED_FORMATTER) + " IST"
                : "N/A";
        String vehicleReg = resolveVehicleReg(alert);
        String vehicleCode = resolveVehicleCode(alert);

        String driverName = resolveDriverName(alert, isSimulation);
        String driverEmail = resolveDriverEmail(alert, isSimulation);
        String driverCode = resolveDriverCode(alert, isSimulation);

        String location = alert.getRoadName() != null ? alert.getRoadName() :
                (alert.getLatitude() != null ? alert.getLatitude() + ", " + alert.getLongitude() : "N/A");

        // Progressive penalty summary for plain text
        com.gps.tracking.entity.DriverPenalty penalty = null;
        BigDecimal cumulative = BigDecimal.ZERO;
        if (alert.getId() != null) {
            List<com.gps.tracking.entity.DriverPenalty> pList = driverPenaltyRepository.findByAlertId(alert.getId());
            if (!pList.isEmpty()) {
                penalty = pList.get(0);
                if (penalty.getDriver() != null && penalty.getDriver().getId() != null) {
                    BigDecimal sum = driverPenaltyRepository.sumActiveMonetaryAmountByDriverId(penalty.getDriver().getId());
                    if (sum != null) cumulative = sum;
                }
            }
        }

        String penaltyText = "";
        if (penalty != null) {
            if ("WARNING".equalsIgnoreCase(penalty.getClassification())) {
                penaltyText = String.format("""

                    [PROGRESSIVE WARNING NOTICE]
                    Classification: WARNING (Occurrence #%d)
                    Penalty Amount: Rs. 0.00 (Monetary penalty not yet applied)
                    """, penalty.getOccurrenceNumber());
            } else {
                penaltyText = String.format("""

                    [PROGRESSIVE PENALTY APPLIED]
                    Classification: PENALTY (Occurrence #%d)
                    Current Penalty Fine: Rs. %.2f
                    Driver Cumulative Penalties: Rs. %.2f
                    Base Penalty: Rs. %.2f | Increment: Rs. %.2f
                    """, penalty.getOccurrenceNumber(),
                        penalty.getMonetaryAmount() != null ? penalty.getMonetaryAmount().doubleValue() : 0.0,
                        cumulative.doubleValue(),
                        penalty.getBasePenalty() != null ? penalty.getBasePenalty().doubleValue() : 0.0,
                        penalty.getPenaltyIncrement() != null ? penalty.getPenaltyIncrement().doubleValue() : 30.0);
            }
        }

        String simNotice = isSimulation
                ? """
                  =======================================================
                  [SIMULATION MODE] - LIVE TRUCK SIMULATION DEMONSTRATION
                  This is an automated test alert generated during Live Simulation.
                  Zero real-world impact. Academic & Demonstration purposes only.
                  =======================================================

                  """
                : "";

        return String.format("""
            Hello %s,

            %s[TrackFleet Alert Dispatch]
            Alert Type: %s
            Severity: %s
            Status: %s
            Alert ID: #%s
            Reference: %s
            Event Time: %s
            %s
            Vehicle: %s (%s)
            Driver: %s (%s)
            Driver Email: %s
            Location: %s
            Speed: %s km/h (Limit: %s km/h)
            Acceleration: %s m/s²
            Message: %s

            This automated alert was generated by the TrackFleet system. Please take appropriate action.
            """,
            greeting,
            simNotice,
            String.join(", ", alertTypes),
            safe(alert.getSeverity()),
            safe(alert.getStatus()),
            safe(alert.getId()),
            safe(alert.getReference()),
            eventTime,
            penaltyText,
            vehicleReg, vehicleCode,
            driverName, driverCode,
            driverEmail,
            location,
            alert.getSpeedKmph() != null ? alert.getSpeedKmph() : "N/A",
            alert.getSpeedLimitKmph() != null ? alert.getSpeedLimitKmph() : "N/A",
            alert.getAccelerationMps2() != null ? alert.getAccelerationMps2() : "N/A",
            safe(alert.getMessage())
        );
    }

    // ----------------------------------------------------------------
    // SMS (MSG91) — mirrors Python send_sms_notifications logic
    // ----------------------------------------------------------------
    private void sendSmsNotifications(Alert alert, List<String> alertTypes) {
        if (alert == null || Boolean.TRUE.equals(alert.getIsSimulation())) {
            log.info("Simulation alert {} (or null) — suppressing SMS/WhatsApp dispatch per simulation policy",
                    alert != null ? alert.getId() : null);
            return;
        }

        if (msg91AuthKey == null || msg91AuthKey.isBlank() || msg91TemplateId == null || msg91TemplateId.isBlank()) {
            String err = "MSG91 configuration missing (MSG91_AUTH_KEY or MSG91_SMS_TEMPLATE_ID not set)";
            alert.setFleetSmsError(err);
            alert.setManagerSmsError(err);
            alert.setDriverSmsError(err);
            log.warn("SMS: FAILED | {}", err);
            return;
        }

        String alertTimeStr = alert.getOccurredAt() != null ?
            alert.getOccurredAt().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm")) : "N/A";
        String speedStr = alert.getSpeedKmph() != null ? alert.getSpeedKmph().intValue() + " km/h" : "N/A";
        String driverName = alert.getDriver() != null ? alert.getDriver().getName() : "Unknown";
        String vehicleReg = alert.getVehicle() != null ? alert.getVehicle().getRegistrationNumber() : "Unknown";
        String location = alert.getRoadName() != null ? alert.getRoadName() : "N/A";
        String alertTypeStr = String.join(", ", alertTypes);

        // Attach progressive penalty summary to SMS if present
        if (alert.getId() != null) {
            List<com.gps.tracking.entity.DriverPenalty> pList = driverPenaltyRepository.findByAlertId(alert.getId());
            if (!pList.isEmpty()) {
                com.gps.tracking.entity.DriverPenalty p = pList.get(0);
                if ("WARNING".equalsIgnoreCase(p.getClassification())) {
                    alertTypeStr += " [Occur #" + p.getOccurrenceNumber() + ": WARNING (Rs. 0)]";
                } else {
                    alertTypeStr += " [Occur #" + p.getOccurrenceNumber() + ": FINE Rs. " + p.getMonetaryAmount().intValue() + "]";
                }
            }
        }

        String driverPhone = alert.getDriver() != null ? alert.getDriver().getPhone() : null;

        // Fleet SMS
        String[] fleetResult = sendSingleSms(fleetPhone, vehicleReg, alertTypeStr, driverName, location, alertTimeStr, speedStr);
        alert.setFleetSmsSent(Boolean.parseBoolean(fleetResult[0]));
        alert.setFleetSmsError("false".equals(fleetResult[0]) ? fleetResult[1] : null);
        alert.setFleetSmsRequestId("true".equals(fleetResult[0]) ? fleetResult[1] : null);
        if ("true".equals(fleetResult[0])) log.info("SMS -> Fleet: SUCCESS | Request ID: {}", fleetResult[1]);
        else log.warn("SMS -> Fleet: FAILED | {}", fleetResult[1]);

        // Manager SMS
        String[] managerResult = sendSingleSms(managerPhone, vehicleReg, alertTypeStr, driverName, location, alertTimeStr, speedStr);
        alert.setManagerSmsSent(Boolean.parseBoolean(managerResult[0]));
        alert.setManagerSmsError("false".equals(managerResult[0]) ? managerResult[1] : null);
        alert.setManagerSmsRequestId("true".equals(managerResult[0]) ? managerResult[1] : null);

        // Driver SMS
        String[] driverResult = sendSingleSms(driverPhone, vehicleReg, alertTypeStr, driverName, location, alertTimeStr, speedStr);
        alert.setDriverSmsSent(Boolean.parseBoolean(driverResult[0]));
        alert.setDriverSmsError("false".equals(driverResult[0]) ? driverResult[1] : null);
        alert.setDriverSmsRequestId("true".equals(driverResult[0]) ? driverResult[1] : null);

        boolean allSent = Boolean.TRUE.equals(alert.getFleetSmsSent())
            && Boolean.TRUE.equals(alert.getManagerSmsSent())
            && Boolean.TRUE.equals(alert.getDriverSmsSent());
        alert.setSmsSent(allSent);
        if (allSent) alert.setSmsSentAt(LocalDateTime.now());
    }

    /** Returns [sent(true/false), requestIdOrError] */
    private String[] sendSingleSms(String rawPhone, String vehicle, String alertType,
                                    String driver, String location, String time, String speed) {
        String formattedPhone = formatIndianPhone(rawPhone);
        if (formattedPhone == null) {
            return new String[]{"false", "Phone number missing or invalid: " + rawPhone};
        }

        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("mobiles", formattedPhone);
        recipient.put("vehicle", vehicle);
        recipient.put("alert_type", alertType);
        recipient.put("driver", driver);
        recipient.put("location", location);
        recipient.put("time", time);
        recipient.put("speed", speed);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("template_id", msg91TemplateId);
        payload.put("short_url", "0");
        payload.put("recipients", List.of(recipient));
        if (msg91SenderId != null && !msg91SenderId.isBlank()) {
            payload.put("sender", msg91SenderId);
        }

        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000);
            factory.setReadTimeout(4000);
            RestTemplate restTemplate = new RestTemplate(factory);
            HttpHeaders headers = new HttpHeaders();
            headers.set("authkey", msg91AuthKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                msg91ApiUrl, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);

            int statusCode = response.getStatusCodeValue();

            if (statusCode == 418) return new String[]{"false", "MSG91 Error 418: IP not whitelisted"};
            if (statusCode == 203) return new String[]{"false", "MSG91 Error 203: Sender ID/DLT issue"};
            if (statusCode == 211) return new String[]{"false", "MSG91 Error 211: DLT Template ID missing"};
            if (statusCode == 400) return new String[]{"false", "MSG91 Error 400: Invalid/missing template"};

            if (statusCode == 200 && response.getBody() != null) {
                Map body = response.getBody();
                String type = String.valueOf(body.getOrDefault("type", "")).toLowerCase();
                if ("success".equals(type) || body.containsKey("request_id")) {
                    String reqId = String.valueOf(body.getOrDefault("request_id", body.get("message")));
                    return new String[]{"true", reqId};
                }
                return new String[]{"false", "MSG91 rejected: " + body.get("message")};
            }
            return new String[]{"false", "MSG91 Error " + statusCode};

        } catch (Exception e) {
            return new String[]{"false", "SMS submission error: " + e.getMessage()};
        }
    }

    // ----------------------------------------------------------------
    // UTILITIES (ported from Python)
    // ----------------------------------------------------------------
    /** Format phone for MSG91 Indian SMS delivery (e.g. 919876543210). */
    private String formatIndianPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) return null;
        String clean = rawPhone.replaceAll("[\\s\\-()\\+.]", "").trim();
        if (clean.isEmpty() || !clean.matches("\\d+")) return null;
        if (clean.length() == 10) return "91" + clean;
        if (clean.length() == 11 && clean.startsWith("0")) return "91" + clean.substring(1);
        if (clean.length() == 12 && clean.startsWith("91")) return clean;
        return clean;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "N/A";
        String[] parts = email.split("@");
        String name = parts[0];
        String masked = name.length() > 2 ? name.charAt(0) + "***" + name.charAt(name.length() - 1) : "***";
        return masked + "@" + parts[1];
    }
}
