package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
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
    @Operation(summary = "Generate monthly driver report PDF")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateDriverMonthly(
            @PathVariable Long driverId, @RequestBody ReportRequestDTO req) {
        req.setDriverId(driverId);
        req.setReportType("MONTHLY");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateDriverReport(req)));
    }

    @PostMapping("/driver/{driverId}/daily")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateDriverDaily(
            @PathVariable Long driverId, @RequestBody ReportRequestDTO req) {
        req.setDriverId(driverId); req.setReportType("DAILY");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateDriverReport(req)));
    }

    @PostMapping("/driver/{driverId}/weekly")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateDriverWeekly(
            @PathVariable Long driverId, @RequestBody ReportRequestDTO req) {
        req.setDriverId(driverId); req.setReportType("WEEKLY");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateDriverReport(req)));
    }

    @PostMapping("/driver/{driverId}/custom")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateDriverCustom(
            @PathVariable Long driverId, @RequestBody ReportRequestDTO req) {
        req.setDriverId(driverId); req.setReportType("CUSTOM");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateDriverReport(req)));
    }

    @PostMapping("/fleet/monthly")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateFleetMonthly(@RequestBody ReportRequestDTO req) {
        req.setReportType("MONTHLY");
        return ResponseEntity.ok(ApiResponse.ok(reportService.generateFleetReport(req)));
    }

    /**
     * POST /api/reports/overall
     * Generates an overall fleet summary for a given period.
     * reportType in body: DAILY | WEEKLY | MONTHLY | CUSTOM
     * Returns OverallReportDTO with real DB counts + PDF link.
     */
    @PostMapping("/overall")
    @Operation(summary = "Generate overall fleet report (vehicle entry/exit, alerts, driver breakdown)")
    public ResponseEntity<ApiResponse<OverallReportDTO>> generateOverall(@RequestBody ReportRequestDTO req) {
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
