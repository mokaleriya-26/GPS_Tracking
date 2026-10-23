package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.security.SecurityUtils;
import com.gps.tracking.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.File;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reports", description = "Report generation (server-side PDF)")
public class ReportController {
    private final ReportService reportService;

    @PostMapping("/driver/{driverId}/monthly")
    @Operation(summary = "Generate monthly driver report PDF (Data isolation enforced)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateDriverMonthly(
            @PathVariable Long driverId, @RequestBody ReportRequestDTO req) {
        SecurityUtils.validateDriverAccess(driverId);
        req.setDriverId(driverId);
        req.setReportType("MONTHLY");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateDriverReport(req)));
    }

    @PostMapping("/driver/{driverId}/daily")
    @Operation(summary = "Generate daily driver report PDF (Data isolation enforced)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateDriverDaily(
            @PathVariable Long driverId, @RequestBody ReportRequestDTO req) {
        SecurityUtils.validateDriverAccess(driverId);
        req.setDriverId(driverId);
        req.setReportType("DAILY");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateDriverReport(req)));
    }

    @PostMapping("/driver/{driverId}/weekly")
    @Operation(summary = "Generate weekly driver report PDF (Data isolation enforced)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateDriverWeekly(
            @PathVariable Long driverId, @RequestBody ReportRequestDTO req) {
        SecurityUtils.validateDriverAccess(driverId);
        req.setDriverId(driverId);
        req.setReportType("WEEKLY");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateDriverReport(req)));
    }

    @PostMapping("/driver/{driverId}/custom")
    @Operation(summary = "Generate custom driver report PDF (Data isolation enforced)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateDriverCustom(
            @PathVariable Long driverId, @RequestBody ReportRequestDTO req) {
        SecurityUtils.validateDriverAccess(driverId);
        req.setDriverId(driverId);
        req.setReportType("CUSTOM");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateDriverReport(req)));
    }

    @PostMapping("/fleet/monthly")
    @Operation(summary = "Generate fleet monthly report PDF (Admin only)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateFleetMonthly(@RequestBody ReportRequestDTO req) {
        SecurityUtils.requireAdmin();
        req.setReportType("MONTHLY");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateFleetReport(req)));
    }

    @PostMapping("/fleet/daily")
    @Operation(summary = "Generate fleet daily report PDF (Admin only)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateFleetDaily(@RequestBody ReportRequestDTO req) {
        SecurityUtils.requireAdmin();
        req.setReportType("DAILY");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateFleetReport(req)));
    }

    @PostMapping("/fleet/weekly")
    @Operation(summary = "Generate fleet weekly report PDF (Admin only)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateFleetWeekly(@RequestBody ReportRequestDTO req) {
        SecurityUtils.requireAdmin();
        req.setReportType("WEEKLY");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateFleetReport(req)));
    }

    @PostMapping("/fleet/custom")
    @Operation(summary = "Generate fleet custom report PDF (Admin only)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateFleetCustom(@RequestBody ReportRequestDTO req) {
        SecurityUtils.requireAdmin();
        req.setReportType("CUSTOM");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateFleetReport(req)));
    }

    @PostMapping("/fleet")
    @Operation(summary = "Generate fleet report PDF (Admin only)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateFleet(@RequestBody ReportRequestDTO req) {
        SecurityUtils.requireAdmin();
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateFleetReport(req)));
    }

    @PostMapping(value = {"/driver/{driverId}/alert-based", "/driver/{driverId}/alert"})
    @Operation(summary = "Generate alert-based driver report PDF (Data isolation enforced)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateDriverAlertBased(
            @PathVariable Long driverId, @RequestBody ReportRequestDTO req) {
        SecurityUtils.validateDriverAccess(driverId);
        req.setDriverId(driverId);
        req.setReportType("ALERT_BASED");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateDriverAlertReport(req)));
    }

    @PostMapping(value = {"/fleet/alert-based", "/fleet/alert"})
    @Operation(summary = "Generate alert-based fleet report PDF (Admin only)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateFleetAlertBased(
            @RequestBody ReportRequestDTO req) {
        SecurityUtils.requireAdmin();
        req.setReportType("ALERT_BASED");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateFleetAlertReport(req)));
    }

    @PostMapping("/alert-based")
    @Operation(summary = "Generate alert-based report (driver or fleet based on request scope)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateAlertBased(
            @RequestBody ReportRequestDTO req) {
        req.setReportType("ALERT_BASED");
        if (req.getDriverId() != null || req.getDriverCode() != null) {
            if (req.getDriverId() != null) {
                SecurityUtils.validateDriverAccess(req.getDriverId());
            }
            return ResponseEntity.ok(ApiResponse.ok(reportService.generateDriverAlertReport(req)));
        } else {
            SecurityUtils.requireAdmin();
            return ResponseEntity.ok(ApiResponse.ok(reportService.generateFleetAlertReport(req)));
        }
    }

    @PostMapping("/overall")
    @Operation(summary = "Generate overall fleet report (Admin only)")
    public ResponseEntity<ApiResponse<OverallReportDTO>> generateOverall(@RequestBody ReportRequestDTO req) {
        SecurityUtils.requireAdmin();
        OverallReportDTO result = reportService.generateOverallReport(req);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{reportId}/pdf")
    @Operation(summary = "View PDF inline in browser")
    public ResponseEntity<FileSystemResource> viewPdf(@PathVariable String reportId) {
        return servePdf(reportId, false);
    }

    @GetMapping("/{reportId}/download")
    @Operation(summary = "Download PDF file")
    public ResponseEntity<FileSystemResource> downloadPdf(@PathVariable String reportId) {
        return servePdf(reportId, true);
    }

    private ResponseEntity<FileSystemResource> servePdf(String reportId, boolean download) {
        File file = new File(reportService.getReportFilePath(reportId));
        if (!file.exists()) return ResponseEntity.notFound().build();
        FileSystemResource resource = new FileSystemResource(file);
        ContentDisposition cd = download
            ? ContentDisposition.attachment().filename(file.getName()).build()
            : ContentDisposition.inline().filename(file.getName()).build();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
            .contentType(MediaType.APPLICATION_PDF)
            .body(resource);
    }
}
