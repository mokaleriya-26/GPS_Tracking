package com.gps.tracking.service;

import com.gps.tracking.dto.SafetyScoreBreakdownDTO;
import com.gps.tracking.entity.AlertPenaltyConfig;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.repository.AlertPenaltyConfigRepository;
import com.gps.tracking.repository.DriverPenaltyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SafetyScoreServiceTest {

    @Mock
    private AlertPenaltyConfigRepository configRepo;

    @Mock
    private DriverPenaltyRepository driverPenaltyRepo;

    @InjectMocks
    private SafetyScoreService safetyScoreService;

    private Driver driver;

    @BeforeEach
    void setUp() {
        driver = Driver.builder()
                .id(1L)
                .code("DRV001")
                .name("Aarav Sharma")
                .build();

        when(configRepo.findByIsActiveTrue()).thenReturn(List.of(
                AlertPenaltyConfig.builder().alertType("Overspeed Alert").weight(BigDecimal.valueOf(5.0)).build(),
                AlertPenaltyConfig.builder().alertType("Harsh Braking Alert").weight(BigDecimal.valueOf(4.0)).build(),
                AlertPenaltyConfig.builder().alertType("GPS Disconnect Alert").weight(BigDecimal.valueOf(6.0)).build(),
                AlertPenaltyConfig.builder().alertType("Night Driving Alert").weight(BigDecimal.valueOf(3.0)).build()
        ));
    }

    @Test
    void testCleanDrivingGivesPerfectScore() {
        when(driverPenaltyRepo.sumActiveSafetyPenaltyPointsInDateRange(anyLong(), any(), any())).thenReturn(0.0);

        SafetyScoreBreakdownDTO result = safetyScoreService.calculateDriverScore(
                driver, 20, 2000.0, 0, 0, 0, 0, 0, 0.0, 0,
                LocalDateTime.now().minusDays(30), LocalDateTime.now());

        assertNotNull(result);
        assertEquals(new BigDecimal("100.00"), result.getFinalSafetyScore());
        assertEquals("Excellent", result.getPerformanceCategory());
        assertEquals(new BigDecimal("0.00"), result.getTotalWeightedPenalty());
    }

    @Test
    void testNormalizedFormulaDeductionsAndClamping() {
        when(driverPenaltyRepo.sumActiveSafetyPenaltyPointsInDateRange(anyLong(), any(), any())).thenReturn(5.0);

        // 10 overspeed in 1000 km -> rate = 10 -> penalty = 10 * 5 = 50 pts
        // 5 braking in 1000 km -> rate = 5 -> penalty = 5 * 4 = 20 pts
        // manual penalty = 5 pts
        // Total penalty = 75 pts
        // Expected score = 100 - 75 = 25.00 ("Needs Improvement")
        SafetyScoreBreakdownDTO result = safetyScoreService.calculateDriverScore(
                driver, 10, 1000.0, 10, 5, 0, 0, 0, 0.0, 0,
                LocalDateTime.now().minusDays(30), LocalDateTime.now());

        assertNotNull(result);
        assertEquals(new BigDecimal("25.00"), result.getFinalSafetyScore());
        assertEquals("Needs Improvement", result.getPerformanceCategory());
        assertEquals(new BigDecimal("75.00"), result.getTotalWeightedPenalty());
    }

    @Test
    void testScoreClampedAtZeroForExcessiveEvents() {
        when(driverPenaltyRepo.sumActiveSafetyPenaltyPointsInDateRange(anyLong(), any(), any())).thenReturn(0.0);

        // 50 overspeed in 100 km -> rate = 500 -> penalty = 2500 pts
        SafetyScoreBreakdownDTO result = safetyScoreService.calculateDriverScore(
                driver, 5, 100.0, 50, 0, 0, 0, 0, 0.0, 0,
                LocalDateTime.now().minusDays(30), LocalDateTime.now());

        assertEquals(new BigDecimal("0.00"), result.getFinalSafetyScore());
        assertEquals("Needs Improvement", result.getPerformanceCategory());
    }
}
