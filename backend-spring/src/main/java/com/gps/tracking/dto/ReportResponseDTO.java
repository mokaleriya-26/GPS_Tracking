package com.gps.tracking.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReportResponseDTO {
    private String reportId;
    private String reportType;
    private String status; // GENERATING, READY, FAILED
    private String pdfUrl;
    private String downloadUrl;
    private LocalDateTime generatedAt;
    private String message;
    private Object reportData; // Summary data for display in UI
}
