package com.gps.tracking.service;

import com.gps.tracking.dto.PenaltyDTO;
import com.gps.tracking.dto.PenaltyRequestDTO;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.entity.DriverPenalty;
import com.gps.tracking.exception.ValidationException;
import com.gps.tracking.repository.AlertPenaltyConfigRepository;
import com.gps.tracking.repository.AlertRepository;
import com.gps.tracking.repository.DriverPenaltyRepository;
import com.gps.tracking.repository.DriverRepository;
import com.gps.tracking.repository.PenaltyAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PenaltyServiceTest {

    @Mock
    private AlertPenaltyConfigRepository configRepo;

    @Mock
    private DriverPenaltyRepository penaltyRepo;

    @Mock
    private PenaltyAuditLogRepository auditRepo;

    @Mock
    private DriverRepository driverRepo;

    @Mock
    private AlertRepository alertRepo;

    @InjectMocks
    private PenaltyService penaltyService;

    private Driver driver;

    @BeforeEach
    void setUp() {
        driver = Driver.builder().id(1L).code("DRV001").name("Aarav Sharma").build();
    }

    @Test
    void testRecordPenaltySuccess() {
        when(driverRepo.findById(1L)).thenReturn(Optional.of(driver));
        when(penaltyRepo.save(any(DriverPenalty.class))).thenAnswer(invocation -> {
            DriverPenalty p = invocation.getArgument(0);
            p.setId(101L);
            return p;
        });

        PenaltyRequestDTO req = PenaltyRequestDTO.builder()
                .driverId(1L)
                .penaltyType("MANUAL_SAFETY_DEDUCTION")
                .reason("Unauthorized route deviation")
                .penaltyPoints(BigDecimal.valueOf(5.0))
                .monetaryAmount(BigDecimal.valueOf(250.0))
                .deductFromScore(true)
                .build();

        PenaltyDTO result = penaltyService.recordPenalty(req, "Fleet Manager");

        assertNotNull(result);
        assertEquals(101L, result.getId());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("Unauthorized route deviation", result.getReason());
        verify(auditRepo, times(1)).save(any());
    }

    @Test
    void testPreventDuplicateScoreDeductionOnSameAlert() {
        when(driverRepo.findById(1L)).thenReturn(Optional.of(driver));
        when(penaltyRepo.countByDriverIdAndAlertIdAndStatus(eq(1L), any(), eq("ACTIVE"))).thenReturn(1L);

        PenaltyRequestDTO req = PenaltyRequestDTO.builder()
                .driverId(1L)
                .alertId(999L)
                .penaltyType("MANUAL_SAFETY_DEDUCTION")
                .reason("Duplicate penalty attempt")
                .penaltyPoints(BigDecimal.valueOf(5.0))
                .deductFromScore(true)
                .build();

        assertThrows(ValidationException.class, () -> penaltyService.recordPenalty(req, "Fleet Manager"));
    }

    @Test
    void testRevokePenaltyDeactivatesDeductionAndAudits() {
        DriverPenalty activePenalty = DriverPenalty.builder()
                .id(202L)
                .driver(driver)
                .status("ACTIVE")
                .deductFromScore(true)
                .penaltyPoints(BigDecimal.valueOf(5.0))
                .reason("Speeding in depot")
                .build();

        when(penaltyRepo.findById(202L)).thenReturn(Optional.of(activePenalty));
        when(penaltyRepo.save(any(DriverPenalty.class))).thenAnswer(i -> i.getArgument(0));

        PenaltyDTO revoked = penaltyService.revokePenalty(202L, "Sensor malfunction verified", "Fleet Manager");

        assertEquals("REVOKED", revoked.getStatus());
        assertFalse(revoked.getDeductFromScore());
        verify(auditRepo, times(1)).save(any());
    }
}
