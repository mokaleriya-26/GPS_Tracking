package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.entity.Vehicle;
import com.gps.tracking.repository.DriverRepository;
import com.gps.tracking.repository.VehicleRepository;
import com.gps.tracking.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DriverReportAccessAndIsolationTest {

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private ReportController reportController;

    @Autowired
    private DriverPortalController driverPortalController;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    private Driver driver1;
    private Driver driver2;
    private Vehicle vehicle1;

    @BeforeEach
    void setUp() {
        driver1 = driverRepository.findByCode("DRV001").orElse(null);
        if (driver1 == null) {
            driver1 = driverRepository.findAll().get(0);
        }

        List<Driver> allDrivers = driverRepository.findAll();
        driver2 = allDrivers.stream()
                .filter(d -> !d.getId().equals(driver1.getId()))
                .findFirst()
                .orElse(null);

        vehicle1 = vehicleRepository.findAll().get(0);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsDriver(Driver driver) {
        UserPrincipal principal = UserPrincipal.builder()
                .username("VH_TEST_" + driver.getCode())
                .role("ROLE_USER")
                .driverId(driver.getId())
                .driverCode(driver.getCode())
                .driverName(driver.getName())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void authenticateAsAdmin() {
        UserPrincipal principal = UserPrincipal.builder()
                .username("test")
                .role("ROLE_ADMIN")
                .driverName("Admin User")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    // =========================================================================
    // 1. ALL 5 REPORT TYPES FOR DRIVER 1
    // =========================================================================

    @Test
    @DisplayName("Driver 1 can generate Monthly Report filtered strictly to Driver 1")
    void testDriver1MonthlyReport() {
        authenticateAsDriver(driver1);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setMonth("2026-09");
        ResponseEntity<ApiResponse<ReportResponseDTO>> resp =
                reportController.generateDriverMonthly(driver1.getId(), req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess());

        ReportResponseDTO data = resp.getBody().getData();
        assertEquals("MONTHLY", data.getReportType());
        assertTrue(data.getReportId().contains(driver1.getCode()));

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) data.getReportData();
        assertEquals(driver1.getName(), map.get("driverName"));
        assertEquals(driver1.getCode(), map.get("driverCode"));
    }

    @Test
    @DisplayName("Driver 1 can generate Weekly Report filtered strictly to Driver 1")
    void testDriver1WeeklyReport() {
        authenticateAsDriver(driver1);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setWeek("2026-W38");
        ResponseEntity<ApiResponse<ReportResponseDTO>> resp =
                reportController.generateDriverWeekly(driver1.getId(), req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        ReportResponseDTO data = resp.getBody().getData();
        assertEquals("WEEKLY", data.getReportType());
        assertTrue(data.getReportId().contains(driver1.getCode()));
    }

    @Test
    @DisplayName("Driver 1 can generate Daily Report filtered strictly to Driver 1")
    void testDriver1DailyReport() {
        authenticateAsDriver(driver1);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setDate("2026-09-15");
        ResponseEntity<ApiResponse<ReportResponseDTO>> resp =
                reportController.generateDriverDaily(driver1.getId(), req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        ReportResponseDTO data = resp.getBody().getData();
        assertEquals("DAILY", data.getReportType());
        assertTrue(data.getReportId().contains(driver1.getCode()));
    }

    @Test
    @DisplayName("Driver 1 can generate Custom Date Range Report filtered strictly to Driver 1")
    void testDriver1CustomReport() {
        authenticateAsDriver(driver1);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setStartDate(LocalDate.of(2026, 9, 1));
        req.setEndDate(LocalDate.of(2026, 9, 20));
        ResponseEntity<ApiResponse<ReportResponseDTO>> resp =
                reportController.generateDriverCustom(driver1.getId(), req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        ReportResponseDTO data = resp.getBody().getData();
        assertEquals("CUSTOM", data.getReportType());
        assertTrue(data.getReportId().contains(driver1.getCode()));
    }

    @Test
    @DisplayName("Driver 1 can generate Alert-Based Report filtered strictly to Driver 1")
    void testDriver1AlertBasedReport() {
        authenticateAsDriver(driver1);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setStartDate(LocalDate.of(2026, 9, 1));
        req.setEndDate(LocalDate.of(2026, 9, 30));
        req.setAlertTypes(List.of("Overspeed Alert", "Harsh Braking Alert"));

        ResponseEntity<ApiResponse<ReportResponseDTO>> resp =
                reportController.generateDriverAlertBased(driver1.getId(), req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        ReportResponseDTO data = resp.getBody().getData();
        assertEquals("ALERT_BASED", data.getReportType());
        assertTrue(data.getReportId().contains(driver1.getCode()));
    }

    // =========================================================================
    // 2. DRIVER 2 SEES DRIVER 2'S DATA (NOT DRIVER 1'S)
    // =========================================================================

    @Test
    @DisplayName("Driver 2 generates Monthly Report and receives only Driver 2's identity")
    void testDriver2MonthlyReportIsolation() {
        authenticateAsDriver(driver2);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setMonth("2026-09");
        ResponseEntity<ApiResponse<ReportResponseDTO>> resp =
                reportController.generateDriverMonthly(driver2.getId(), req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        ReportResponseDTO data = resp.getBody().getData();
        assertTrue(data.getReportId().contains(driver2.getCode()));
        assertFalse(data.getReportId().contains(driver1.getCode()));

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) data.getReportData();
        assertEquals(driver2.getName(), map.get("driverName"));
        assertEquals(driver2.getCode(), map.get("driverCode"));
    }

    // =========================================================================
    // 3. CROSS-DRIVER ACCESS ATTEMPTS FORBIDDEN
    // =========================================================================

    @Test
    @DisplayName("Driver 2 attempting to access Driver 1's Monthly Report throws AccessDeniedException")
    void testDriver2AccessDriver1MonthlyReportDenied() {
        authenticateAsDriver(driver2);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setMonth("2026-09");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                reportController.generateDriverMonthly(driver1.getId(), req)
        );
        assertTrue(ex.getMessage().contains("Data isolation violation"));
    }

    @Test
    @DisplayName("Driver 2 attempting to access Driver 1's Alert-Based Report throws AccessDeniedException")
    void testDriver2AccessDriver1AlertBasedReportDenied() {
        authenticateAsDriver(driver2);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setStartDate(LocalDate.of(2026, 9, 1));
        req.setEndDate(LocalDate.of(2026, 9, 30));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                reportController.generateDriverAlertBased(driver1.getId(), req)
        );
        assertTrue(ex.getMessage().contains("Data isolation violation"));
    }

    @Test
    @DisplayName("Driver 2 attempting to spoof driverId on generic /alert-based endpoint throws AccessDeniedException")
    void testDriver2SpoofDriverIdOnAlertBasedDenied() {
        authenticateAsDriver(driver2);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setDriverId(driver1.getId());
        req.setStartDate(LocalDate.of(2026, 9, 1));
        req.setEndDate(LocalDate.of(2026, 9, 30));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                reportController.generateAlertBased(req)
        );
        assertTrue(ex.getMessage().contains("Data isolation violation"));
    }

    @Test
    @DisplayName("Driver 2 attempting to spoof driverCode on generic /alert-based endpoint throws AccessDeniedException")
    void testDriver2SpoofDriverCodeOnAlertBasedDenied() {
        authenticateAsDriver(driver2);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setDriverCode(driver1.getCode());
        req.setStartDate(LocalDate.of(2026, 9, 1));
        req.setEndDate(LocalDate.of(2026, 9, 30));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                reportController.generateAlertBased(req)
        );
        assertTrue(ex.getMessage().contains("Data isolation violation"));
    }

    // =========================================================================
    // 4. FLEET-WIDE REPORT ACCESS FORBIDDEN TO DRIVERS
    // =========================================================================

    @Test
    @DisplayName("Driver attempting to generate Fleet-Wide Monthly report throws AccessDeniedException")
    void testDriverCannotGenerateFleetMonthlyReport() {
        authenticateAsDriver(driver1);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setMonth("2026-09");

        assertThrows(AccessDeniedException.class, () ->
                reportController.generateFleetMonthly(req)
        );
    }

    @Test
    @DisplayName("Driver attempting to generate Fleet-Wide Alert report throws AccessDeniedException")
    void testDriverCannotGenerateFleetAlertReport() {
        authenticateAsDriver(driver1);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setStartDate(LocalDate.of(2026, 9, 1));
        req.setEndDate(LocalDate.of(2026, 9, 30));

        assertThrows(AccessDeniedException.class, () ->
                reportController.generateFleetAlertBased(req)
        );
    }

    @Test
    @DisplayName("Driver attempting to generate Overall Fleet report throws AccessDeniedException")
    void testDriverCannotGenerateOverallReport() {
        authenticateAsDriver(driver1);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setMonth("2026-09");

        assertThrows(AccessDeniedException.class, () ->
                reportController.generateOverall(req)
        );
    }

    // =========================================================================
    // 5. PDF ACCESS ISOLATION
    // =========================================================================

    @Test
    @DisplayName("Driver attempting to access Fleet PDF throws AccessDeniedException")
    void testDriverCannotAccessFleetPdf() {
        authenticateAsDriver(driver1);

        assertThrows(AccessDeniedException.class, () ->
                reportController.viewPdf("FLTRPT_MONTHLY_2026-09-01_abc123")
        );
    }

    @Test
    @DisplayName("Driver 2 attempting to access Driver 1's PDF throws AccessDeniedException")
    void testDriver2CannotAccessDriver1Pdf() {
        authenticateAsDriver(driver2);

        assertThrows(AccessDeniedException.class, () ->
                reportController.viewPdf("DRVRPT_" + driver1.getCode() + "_MONTHLY_2026-09-01_abc123")
        );
    }

    @Test
    @DisplayName("Unauthenticated user attempting to access PDF throws AccessDeniedException")
    void testUnauthenticatedCannotAccessPdf() {
        SecurityContextHolder.clearContext();

        assertThrows(AccessDeniedException.class, () ->
                reportController.viewPdf("DRVRPT_DRV001_MONTHLY_2026-09-01_abc123")
        );
    }

    // =========================================================================
    // 6. DRIVER PORTAL REPORTS ENDPOINT
    // =========================================================================

    @Test
    @DisplayName("Driver generates report through driver-portal endpoint and gets verified driver data")
    void testDriverPortalGenerateReport() {
        authenticateAsDriver(driver1);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setReportType("MONTHLY");
        req.setMonth("2026-09");

        ResponseEntity<ApiResponse<ReportResponseDTO>> resp =
                driverPortalController.generateReport(req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().getData().getReportId().contains(driver1.getCode()));
    }

    // =========================================================================
    // 7. EMPTY STATE DATE RANGE
    // =========================================================================

    @Test
    @DisplayName("Date range with no records returns safe empty metrics with 0 trips and 0 alerts")
    void testEmptyStateDateRange() {
        authenticateAsDriver(driver1);

        ReportRequestDTO req = new ReportRequestDTO();
        req.setStartDate(LocalDate.of(2018, 1, 1));
        req.setEndDate(LocalDate.of(2018, 1, 5));

        ResponseEntity<ApiResponse<ReportResponseDTO>> resp =
                reportController.generateDriverCustom(driver1.getId(), req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        ReportResponseDTO data = resp.getBody().getData();

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) data.getReportData();
        assertEquals(0, map.get("totalTrips"));
        assertEquals(0, map.get("totalAlerts"));
    }

    // =========================================================================
    // 8. ADMIN CAN STILL ACCESS FLEET-WIDE REPORTS
    // =========================================================================

    @Test
    @DisplayName("Admin can successfully generate fleet-wide reports")
    void testAdminFleetReportAccess() {
        authenticateAsAdmin();

        ReportRequestDTO req = new ReportRequestDTO();
        req.setMonth("2026-09");

        ResponseEntity<ApiResponse<ReportResponseDTO>> resp =
                reportController.generateFleetMonthly(req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody().getData());
        assertTrue(resp.getBody().getData().getReportId().startsWith("FLTRPT_"));
    }
}

