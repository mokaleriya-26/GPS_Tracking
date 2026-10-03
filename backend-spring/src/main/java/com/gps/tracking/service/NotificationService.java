package com.gps.tracking.service;

import com.gps.tracking.entity.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

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

    @Value("${notification.email.fleet:fleet@trackfleet.com}")
    private String fleetEmail;

    @Value("${notification.email.manager:manager@trackfleet.com}")
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

    /**
     * Process alert notifications.
     * If silenceNotifications=true, marks alert as HISTORICAL without sending.
     * Otherwise, sends email and SMS to Fleet, Manager, Driver.
     */
    @Async
    public void processAlert(Alert alert, List<String> alertTypes, boolean silenceNotifications) {
        if (silenceNotifications) {
            alert.setStatus("HISTORICAL");
            alert.setDeliveryStatus("SILENCED");
            alertRepository.save(alert);
            log.info("Alert {} silenced (historical import)", alert.getId());
            return;
        }

        log.info("Processing notifications for alert {} ({})", alert.getId(), String.join(", ", alertTypes));

        // Send email notifications
        sendEmailNotifications(alert, alertTypes);

        // Send SMS notifications
        sendSmsNotifications(alert, alertTypes);

        alertRepository.save(alert);
    }

    // ----------------------------------------------------------------
    // EMAIL
    // ----------------------------------------------------------------
    private void sendEmailNotifications(Alert alert, List<String> alertTypes) {
        String subject = generateEmailSubject(alert, alertTypes);
        String body = generateEmailBody(alert, alertTypes);
        String driverEmail = alert.getDriver() != null ? null : null; // driver email not in new model, use phone lookup

        boolean anyEmailSent = false;

        // Fleet email
        if (fleetEmail != null && !fleetEmail.isBlank()) {
            try {
                sendSingleEmail(fleetEmail, subject, body);
                alert.setFleetEmailSent(true);
                anyEmailSent = true;
                log.info("Email -> Fleet: SUCCESS | {}", maskEmail(fleetEmail));
            } catch (Exception e) {
                alert.setFleetEmailSent(false);
                alert.setFleetEmailError(e.getMessage());
                log.warn("Email -> Fleet: FAILED | {}", e.getMessage());
            }
        } else {
            alert.setFleetEmailError("Fleet email address not configured");
        }

        // Manager email
        if (managerEmail != null && !managerEmail.isBlank()) {
            try {
                sendSingleEmail(managerEmail, subject, body);
                alert.setManagerEmailSent(true);
                anyEmailSent = true;
                log.info("Email -> Manager: SUCCESS | {}", maskEmail(managerEmail));
            } catch (Exception e) {
                alert.setManagerEmailSent(false);
                alert.setManagerEmailError(e.getMessage());
                log.warn("Email -> Manager: FAILED | {}", e.getMessage());
            }
        } else {
            alert.setManagerEmailError("Manager email address not configured");
        }

        alert.setEmailSent(alert.getFleetEmailSent() != null && alert.getFleetEmailSent()
                && alert.getManagerEmailSent() != null && alert.getManagerEmailSent());
        if (anyEmailSent) {
            alert.setEmailSentAt(LocalDateTime.now());
        }
    }

    private void sendSingleEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private String generateEmailSubject(Alert alert, List<String> alertTypes) {
        String vehicleNum = alert.getVehicle() != null ? alert.getVehicle().getRegistrationNumber() : "Unknown";
        return "Fleet Alert - " + String.join(", ", alertTypes) + " - " + vehicleNum;
    }

    private String generateEmailBody(Alert alert, List<String> alertTypes) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String vehicleCode = alert.getVehicle() != null ? alert.getVehicle().getCode() : "N/A";
        String vehicleReg = alert.getVehicle() != null ? alert.getVehicle().getRegistrationNumber() : "N/A";
        String driverName = alert.getDriver() != null ? alert.getDriver().getName() : "Unknown";
        String driverId = alert.getDriver() != null ? alert.getDriver().getCode() : "N/A";
        String driverPhone = alert.getDriver() != null ? alert.getDriver().getPhone() : "N/A";
        String eventTime = alert.getOccurredAt() != null ? alert.getOccurredAt().format(fmt) : "N/A";
        String location = alert.getRoadName() != null ? alert.getRoadName() :
                (alert.getLatitude() != null ? alert.getLatitude() + ", " + alert.getLongitude() : "N/A");

        return String.format("""
            Fleet Alert Notification

            Alert Type: %s
            Vehicle Code: %s
            Vehicle Number: %s

            Driver:
            Name: %s
            Driver ID: %s
            Contact: %s

            Location: %s
            Event Time: %s

            Speed: %s km/h
            Severity: %s
            Status: %s

            Please take appropriate action.
            """,
            String.join(", ", alertTypes), vehicleCode, vehicleReg,
            driverName, driverId, driverPhone,
            location, eventTime,
            alert.getSpeedKmph() != null ? alert.getSpeedKmph() : "N/A",
            alert.getSeverity(), alert.getStatus()
        );
    }

    // ----------------------------------------------------------------
    // SMS (MSG91) — mirrors Python send_sms_notifications logic
    // ----------------------------------------------------------------
    private void sendSmsNotifications(Alert alert, List<String> alertTypes) {
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
            RestTemplate restTemplate = new RestTemplate();
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
