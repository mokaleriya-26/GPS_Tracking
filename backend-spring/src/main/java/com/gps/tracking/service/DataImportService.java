package com.gps.tracking.service;

import com.gps.tracking.entity.*;
import com.gps.tracking.repository.*;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DataImportService — Spring Boot port of Django's poll_csv.py management command.
 *
 * Behaviour:
 * - Reads vehicle_fleet_alerts_fake_dataset.csv on startup.
 * - Creates/updates Driver, Vehicle, Trip entities.
 * - Detects all 5 original alert types: Overspeed, Harsh Braking, GPS Disconnect, Night Driving, Ignition.
 * - Generates deterministic reference (identical to old event_id) to avoid duplicate alerts.
 * - ALL CSV imports are ALWAYS treated as HISTORICAL/SILENCED — the CSV is a static
 *   historical dataset, NOT a live event stream. NotificationService is NEVER called
 *   from this class. Only the live telemetry API triggers notifications.
 * - Deduplication: rows whose reference already exists in the DB are skipped silently.
 *
 * Runs once at startup via ApplicationRunner.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class DataImportService implements ApplicationRunner {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final AlertRepository alertRepository;
    // NotificationService intentionally NOT injected here.
    // CSV data is always HISTORICAL. Only the live telemetry API triggers notifications.

    private static final DateTimeFormatter[] PARSERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")
    };

    @Override
    public void run(ApplicationArguments args) {
        // Guard: if rich seed data (seed.sql) is already loaded, skip the legacy CSV import.
        // The seed.sql dataset is far richer (9082 trips, 11089 alerts, 17 drivers, 20 vehicles)
        // than the small CSV (110 rows). Importing the CSV on top of seed data creates duplicates.
        long existingTrips = tripRepository.count();
        if (existingTrips >= 1000) {
            log.info("[CSV IMPORT] Skipped — rich seed data already loaded ({} trips in DB). " +
                     "Remove data/seed.sql data to re-enable CSV import.", existingTrips);
            return;
        }

        // Find the CSV file
        String[] searchPaths = {
            "../dataset/vehicle_fleet_alerts_fake_dataset.csv",
            "../../dataset/vehicle_fleet_alerts_fake_dataset.csv",
            "../data/alerts.csv",
            "data/alerts.csv"
        };

        File csvFile = null;
        for (String path : searchPaths) {
            File f = new File(path);
            if (f.exists()) { csvFile = f; break; }
        }

        if (csvFile == null) {
            log.info("No CSV import file found. Skipping initial data import.");
            log.info("Place vehicle_fleet_alerts_fake_dataset.csv in the dataset/ directory.");
            return;
        }

        log.info("Starting data import from: {}", csvFile.getAbsolutePath());
        log.info("=".repeat(60));
        log.info("[CSV IMPORT] All records are treated as HISTORICAL. No notifications will be sent.");
        log.info("=".repeat(60));

        try {
            processCSV(csvFile);
            log.info("Data import completed successfully.");
        } catch (Exception e) {
            log.error("Error during data import: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void processCSV(File csvFile) throws Exception {
        try (CSVReader reader = new CSVReaderBuilder(new FileReader(csvFile)).build()) {
            String[] headers = reader.readNext();
            if (headers == null) return;

            Map<String, Integer> colIdx = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIdx.put(headers[i].trim(), i);
            }

            String[] row;
            int processed = 0, skipped = 0, newHistorical = 0, alreadyExisted = 0;

            while ((row = reader.readNext()) != null) {
                try {
                    boolean wasNew = processRow(row, colIdx);
                    if (wasNew) newHistorical++;
                    else alreadyExisted++;
                    processed++;
                } catch (Exception e) {
                    log.warn("Skipping CSV row due to error: {}", e.getMessage());
                    skipped++;
                }
            }

            log.info("Import summary: {} rows processed ({} new HISTORICAL, {} already existed, {} skipped)",
                processed + skipped, newHistorical, alreadyExisted, skipped);
        }
    }

    /**
     * Process one CSV row.
     * @return true if a new alert was created, false if it already existed (skipped).
     */
    private boolean processRow(String[] row, Map<String, Integer> colIdx) {
        // 1. Driver
        String driverCode = get(row, colIdx, "Driver ID", "").trim();
        String driverName = get(row, colIdx, "Driver Name", "").trim();
        String driverContact = get(row, colIdx, "Driver Contact", "").trim();

        if (driverCode.isEmpty()) return false;

        Driver driver = driverRepository.findByCode(driverCode).orElse(null);
        if (driver == null) {
            driver = driverRepository.save(Driver.builder()
                .code(driverCode).name(driverName).phone(driverContact).active(true).build());
        } else if (!driverName.isEmpty() && !driverName.equals(driver.getName())) {
            driver.setName(driverName);
            if (!driverContact.isEmpty()) driver.setPhone(driverContact);
            driver = driverRepository.save(driver);
        }

        // 2. Vehicle
        String vehicleCode = get(row, colIdx, "Vehicle ID", "").trim();
        String vehicleReg = get(row, colIdx, "Vehicle Number", "").trim();
        String location = get(row, colIdx, "Location", "").trim();

        if (vehicleCode.isEmpty() || vehicleReg.isEmpty()) return false;

        final Driver finalDriver = driver;
        Vehicle vehicle = vehicleRepository.findByCode(vehicleCode).orElseGet(() ->
            vehicleRepository.save(Vehicle.builder()
                .code(vehicleCode).registrationNumber(vehicleReg)
                .driver(finalDriver).active(true).build()));

        // 3. Trip
        LocalDateTime startTime = parseTime(get(row, colIdx, "Trip Start Time", ""));
        LocalDateTime endTime = parseTime(get(row, colIdx, "Trip End Time", ""));
        BigDecimal distKm = parseDec(get(row, colIdx, "Trip Distance (km)", "0"));
        int durationMin = parseInt(get(row, colIdx, "Trip Duration (min)", "0"));
        BigDecimal speedMax = parseDec(get(row, colIdx, "Speed Max (km/h)", "0"));
        BigDecimal speedMin = parseDec(get(row, colIdx, "Speed Min (km/h)", "0"));

        Trip trip = null;
        if (startTime != null && endTime != null) {
            final LocalDateTime finalStartTime = startTime;
            final Vehicle finalVehicle = vehicle;
            List<Trip> existingTrips = tripRepository.findByVehicleIdAndStartTimeBetweenOrderByStartTimeDesc(
                vehicle.getId(), startTime.minusMinutes(1), startTime.plusMinutes(1));
            if (existingTrips.isEmpty()) {
                trip = tripRepository.save(Trip.builder()
                    .vehicle(finalVehicle).driver(finalDriver)
                    .startTime(startTime).endTime(endTime).status("COMPLETED")
                    .distanceKm(distKm).durationSeconds(durationMin * 60)
                    .maxSpeedKmph(speedMax).minSpeedKmph(speedMin)
                    .build());
            } else {
                trip = existingTrips.get(0);
            }
        }

        // 4. Alert detection (preserving all 5 original alert types)
        List<String> alertTypes = new ArrayList<>();
        if ("yes".equalsIgnoreCase(get(row, colIdx, "Overspeed Alert", "")))
            alertTypes.add("Overspeed Alert");
        if ("yes".equalsIgnoreCase(get(row, colIdx, "Harsh Braking Alert", "")))
            alertTypes.add("Harsh Braking Alert");
        if ("yes".equalsIgnoreCase(get(row, colIdx, "GPS Disconnect Alert", "")))
            alertTypes.add("GPS Disconnect Alert");
        if ("yes".equalsIgnoreCase(get(row, colIdx, "Night Driving Alert", "")))
            alertTypes.add("Night Driving Alert");
        String ignitionRaw = get(row, colIdx, "Ignition On/Off Alert", "");
        if ("yes".equalsIgnoreCase(ignitionRaw) || "on".equalsIgnoreCase(ignitionRaw))
            alertTypes.add("Ignition Alert");

        if (alertTypes.isEmpty()) return false;

        LocalDateTime alertTime = parseTime(get(row, colIdx, "Time", ""));
        if (alertTime == null) alertTime = startTime;
        if (alertTime == null) return false;

        // Generate deterministic reference (matching old event_id format from poll_csv.py)
        String driverCodeClean = driverCode.replace(" ", "");
        String timestampStr = alertTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String tripStartStr = startTime != null ? startTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) : "00000000000000";
        String typeSlug = String.join(", ", alertTypes).replace(" ", "").replace(",", "_");
        String reference = vehicleCode + "_" + vehicleReg + "_" + driverCodeClean + "_" + timestampStr + "_" + tripStartStr + "_" + typeSlug;

        // Deduplication: skip if this exact event already exists in the DB
        if (alertRepository.findByReference(reference).isPresent()) {
            return false;
        }

        String severity = alertTypes.contains("Overspeed Alert") || alertTypes.contains("GPS Disconnect Alert")
            ? "CRITICAL" : "WARNING";

        // ALWAYS create as HISTORICAL/SILENCED — CSV data is historical, never live.
        // NotificationService is deliberately NOT called here.
        Alert alert = alertRepository.save(Alert.builder()
            .reference(reference)
            .alertType(String.join(", ", alertTypes))
            .severity(severity)
            .vehicle(vehicle).driver(driver).trip(trip)
            .occurredAt(alertTime)
            .speedKmph(speedMax)
            .maxSpeedKmph(speedMax).minSpeedKmph(speedMin)
            .roadName(location)
            .tripStartTime(startTime).tripEndTime(endTime)
            .tripDistanceKm(distKm).tripDurationMin(durationMin)
            .status("HISTORICAL")
            .deliveryStatus("SILENCED")
            .emailSent(false).fleetEmailSent(false).managerEmailSent(false).driverEmailSent(false)
            .smsSent(false).fleetSmsSent(false).managerSmsSent(false).driverSmsSent(false)
            .build());

        log.debug("[CSV] Alert id={} type='{}' vehicle={} recorded as HISTORICAL/SILENCED (no notification sent)",
            alert.getId(), String.join(", ", alertTypes), vehicleCode);
        return true;
    }

    // ---- Utilities ----
    private String get(String[] row, Map<String, Integer> colIdx, String key, String def) {
        Integer idx = colIdx.get(key);
        if (idx == null || idx >= row.length) return def;
        String val = row[idx];
        return val != null ? val.trim() : def;
    }

    private LocalDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String clean = raw.replace(" ", "T");
        for (DateTimeFormatter fmt : PARSERS) {
            try { return LocalDateTime.parse(clean.replace("T", " ").trim(), fmt); } catch (Exception ignored) {}
        }
        return null;
    }

    private BigDecimal parseDec(String val) {
        try { return new BigDecimal(val.trim()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private int parseInt(String val) {
        try { return Integer.parseInt(val.trim()); }
        catch (Exception e) { return 0; }
    }
}
