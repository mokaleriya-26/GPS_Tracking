package com.gps.tracking.service;

import com.gps.tracking.dto.SafetyScoreBreakdownDTO;
import com.gps.tracking.dto.SafetyScoreBreakdownDTO.MetricDetailDTO;
import com.gps.tracking.entity.AlertPenaltyConfig;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.repository.AlertPenaltyConfigRepository;
import com.gps.tracking.repository.DriverPenaltyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SafetyScoreService {

    private final AlertPenaltyConfigRepository penaltyConfigRepo;
    private final DriverPenaltyRepository driverPenaltyRepo;

    /**
     * Standardized Driver Safety Score Formula:
     * Safety Score = 100 - Σ(Normalized Event Rate_i × Weight_i) - Manual Penalty Deductions
     * Clamped strictly between 0.0 and 100.0.
     */
    public SafetyScoreBreakdownDTO calculateDriverScore(
            Driver driver,
            int totalTrips,
            double totalDistanceKm,
            long overspeedCount,
            long harshBrakingCount,
            long harshAccelCount,
            long gpsDisconnectCount,
            long nightDrivingCount,
            double nightDrivingHours,
            long fatigueCount,
            LocalDateTime periodFrom,
            LocalDateTime periodTo) {

        Map<String, BigDecimal> weights = loadConfiguredWeights();

        double dist = Math.max(0.0, totalDistanceKm);
        int trips = Math.max(0, totalTrips);

        // 1. Calculate Normalized Rates
        // Distance-based metrics: events per 1,000 km
        double overspeedRate = normalizeDistanceMetric(overspeedCount, dist, trips);
        double harshBrakingRate = normalizeDistanceMetric(harshBrakingCount, dist, trips);
        double harshAccelRate = normalizeDistanceMetric(harshAccelCount, dist, trips);
        double gpsDisconnectRate = normalizeDistanceMetric(gpsDisconnectCount, dist, trips);

        // Night driving: hours of night driving per 1,000 km (or alert count if hours is 0)
        double nightRate = dist > 0
                ? (nightDrivingHours > 0 ? (nightDrivingHours / dist) * 1000.0 : (nightDrivingCount / dist) * 1000.0)
                : (nightDrivingHours > 0 ? nightDrivingHours : (trips > 0 ? (nightDrivingCount / (double) trips) * 10.0 : nightDrivingCount));

        // Fatigue: incidents per 100 trips (or per 1,000 km if trips is 0)
        double fatigueRate = trips > 0
                ? (fatigueCount / (double) trips) * 100.0
                : (dist > 0 ? (fatigueCount / dist) * 1000.0 : fatigueCount);

        List<MetricDetailDTO> metrics = new ArrayList<>();

        // Metric 1: Overspeed
        BigDecimal wOverspeed = weights.getOrDefault("Overspeed Alert", BigDecimal.valueOf(5.0));
        BigDecimal penOverspeed = round(BigDecimal.valueOf(overspeedRate).multiply(wOverspeed));
        metrics.add(MetricDetailDTO.builder()
                .metricName("Overspeed")
                .alertType("Overspeed Alert")
                .count(overspeedCount)
                .normalizedRate(roundDouble(overspeedRate))
                .rateUnit("per 1,000 km")
                .weight(wOverspeed)
                .penalty(penOverspeed)
                .available(true)
                .note("Normalized per 1,000 km driven")
                .build());

        // Metric 2: Harsh Braking
        BigDecimal wBraking = weights.getOrDefault("Harsh Braking Alert", BigDecimal.valueOf(4.0));
        BigDecimal penBraking = round(BigDecimal.valueOf(harshBrakingRate).multiply(wBraking));
        metrics.add(MetricDetailDTO.builder()
                .metricName("Harsh Braking")
                .alertType("Harsh Braking Alert")
                .count(harshBrakingCount)
                .normalizedRate(roundDouble(harshBrakingRate))
                .rateUnit("per 1,000 km")
                .weight(wBraking)
                .penalty(penBraking)
                .available(true)
                .note("Normalized per 1,000 km driven")
                .build());

        // Metric 3: Harsh Acceleration
        BigDecimal wAccel = weights.getOrDefault("Harsh Acceleration Alert", BigDecimal.valueOf(4.0));
        BigDecimal penAccel = round(BigDecimal.valueOf(harshAccelRate).multiply(wAccel));
        metrics.add(MetricDetailDTO.builder()
                .metricName("Harsh Acceleration")
                .alertType("Harsh Acceleration Alert")
                .count(harshAccelCount)
                .normalizedRate(roundDouble(harshAccelRate))
                .rateUnit("per 1,000 km")
                .weight(wAccel)
                .penalty(penAccel)
                .available(true)
                .note("Normalized per 1,000 km driven")
                .build());

        // Metric 4: GPS Disconnect
        BigDecimal wGps = weights.getOrDefault("GPS Disconnect Alert", BigDecimal.valueOf(6.0));
        BigDecimal penGps = round(BigDecimal.valueOf(gpsDisconnectRate).multiply(wGps));
        metrics.add(MetricDetailDTO.builder()
                .metricName("GPS Disconnect")
                .alertType("GPS Disconnect Alert")
                .count(gpsDisconnectCount)
                .normalizedRate(roundDouble(gpsDisconnectRate))
                .rateUnit("per 1,000 km")
                .weight(wGps)
                .penalty(penGps)
                .available(true)
                .note("Normalized per 1,000 km driven")
                .build());

        // Metric 5: Night Driving
        BigDecimal wNight = weights.getOrDefault("Night Driving Alert", BigDecimal.valueOf(3.0));
        BigDecimal penNight = round(BigDecimal.valueOf(nightRate).multiply(wNight));
        metrics.add(MetricDetailDTO.builder()
                .metricName("Night Driving")
                .alertType("Night Driving Alert")
                .count(nightDrivingCount)
                .normalizedRate(roundDouble(nightRate))
                .rateUnit("hours / 1,000 km")
                .weight(wNight)
                .penalty(penNight)
                .available(true)
                .note("Night driving exposure per 1,000 km")
                .build());

        // Metric 6: Fatigue
        BigDecimal wFatigue = weights.getOrDefault("Fatigue Alert", BigDecimal.valueOf(8.0));
        BigDecimal penFatigue = round(BigDecimal.valueOf(fatigueRate).multiply(wFatigue));
        metrics.add(MetricDetailDTO.builder()
                .metricName("Fatigue")
                .alertType("Fatigue Alert")
                .count(fatigueCount)
                .normalizedRate(roundDouble(fatigueRate))
                .rateUnit("per 100 trips")
                .weight(wFatigue)
                .penalty(penFatigue)
                .available(true)
                .note("Normalized per 100 completed trips")
                .build());

        // Sum formula deductions
        BigDecimal formulaDeductions = penOverspeed.add(penBraking)
                .add(penAccel).add(penGps).add(penNight).add(penFatigue);

        // Fetch manual manager safety-score deductions (deduct_from_score = true)
        Double manualDeductionVal = 0.0;
        if (driver != null && driver.getId() != null) {
            if (periodFrom != null && periodTo != null) {
                manualDeductionVal = driverPenaltyRepo.sumActiveSafetyPenaltyPointsInDateRange(
                        driver.getId(), periodFrom, periodTo);
            } else {
                manualDeductionVal = driverPenaltyRepo.sumActiveSafetyPenaltyPoints(driver.getId());
            }
        }
        BigDecimal manualPenaltyDeductions = round(BigDecimal.valueOf(manualDeductionVal != null ? manualDeductionVal : 0.0));

        BigDecimal totalWeightedPenalty = round(formulaDeductions.add(manualPenaltyDeductions));
        BigDecimal baseScore = BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_UP);

        BigDecimal rawScore = baseScore.subtract(totalWeightedPenalty);
        BigDecimal clampedScore = rawScore.max(BigDecimal.ZERO).min(baseScore).setScale(2, RoundingMode.HALF_UP);

        String category;
        if (clampedScore.compareTo(BigDecimal.valueOf(80.00)) >= 0) {
            category = "Excellent";
        } else if (clampedScore.compareTo(BigDecimal.valueOf(60.00)) >= 0) {
            category = "Average";
        } else {
            category = "Needs Improvement";
        }

        String explanation = String.format(
                "Base Score: 100.00 | Formula Deductions: -%.2f (Overspeed: -%.2f, Harsh Braking: -%.2f, Harsh Accel: -%.2f, GPS Disc: -%.2f, Night: -%.2f, Fatigue: -%.2f) | Manual Deductions: -%.2f | Final Score: %.2f (%s)",
                formulaDeductions.doubleValue(),
                penOverspeed.doubleValue(), penBraking.doubleValue(), penAccel.doubleValue(), penGps.doubleValue(), penNight.doubleValue(), penFatigue.doubleValue(),
                manualPenaltyDeductions.doubleValue(),
                clampedScore.doubleValue(), category);

        return SafetyScoreBreakdownDTO.builder()
                .driverId(driver != null ? driver.getId() : null)
                .driverCode(driver != null ? driver.getCode() : null)
                .driverName(driver != null ? driver.getName() : null)
                .totalTrips(trips)
                .totalDistanceKm(round(BigDecimal.valueOf(dist)))
                .metrics(metrics)
                .baseScore(baseScore)
                .formulaDeductions(formulaDeductions)
                .manualPenaltyDeductions(manualPenaltyDeductions)
                .totalWeightedPenalty(totalWeightedPenalty)
                .finalSafetyScore(clampedScore)
                .performanceCategory(category)
                .explanation(explanation)
                .build();
    }

    private double normalizeDistanceMetric(long count, double distanceKm, int trips) {
        if (distanceKm > 0.0) {
            return (count / distanceKm) * 1000.0;
        } else if (trips > 0) {
            return (count / (double) trips) * 10.0;
        } else {
            return (double) count;
        }
    }

    private Map<String, BigDecimal> loadConfiguredWeights() {
        Map<String, BigDecimal> map = new HashMap<>();
        // Default weights
        map.put("Overspeed Alert", BigDecimal.valueOf(5.0));
        map.put("Harsh Braking Alert", BigDecimal.valueOf(4.0));
        map.put("Harsh Acceleration Alert", BigDecimal.valueOf(4.0));
        map.put("GPS Disconnect Alert", BigDecimal.valueOf(6.0));
        map.put("Night Driving Alert", BigDecimal.valueOf(3.0));
        map.put("Fatigue Alert", BigDecimal.valueOf(8.0));
        map.put("Ignition Alert", BigDecimal.valueOf(2.0));

        try {
            List<AlertPenaltyConfig> configs = penaltyConfigRepo.findByIsActiveTrue();
            for (AlertPenaltyConfig c : configs) {
                if (c.getWeight() != null) {
                    map.put(c.getAlertType(), c.getWeight());
                }
            }
        } catch (Exception e) {
            log.warn("Could not load dynamic alert penalty configs: {}", e.getMessage());
        }
        return map;
    }

    private BigDecimal round(BigDecimal val) {
        return val != null ? val.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private double roundDouble(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
