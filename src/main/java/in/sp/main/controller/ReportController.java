package in.sp.main.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import in.sp.main.dto.ReportDTO;
import in.sp.main.service.ReportService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class ReportController {

    private final ReportService reportService;

    /**
     * Returns a JSON attendance report for the given course and date range.
     */
    @GetMapping("/course/{courseId}")
    public ResponseEntity<ReportDTO> getCourseReport(
            @PathVariable Long courseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.buildCourseReport(courseId, startDate, endDate));
    }

    /**
     * Downloads the attendance report as an Excel (.xlsx) file.
     */
    @GetMapping("/course/{courseId}/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @PathVariable Long courseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate)
            throws Exception {

        ReportDTO report = reportService.buildCourseReport(courseId, startDate, endDate);
        byte[] excelBytes = reportService.exportToExcel(report);

        String filename = "attendance-" + report.getCourseCode() + "-"
            + startDate.format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .contentLength(excelBytes.length)
            .body(excelBytes);
    }

    /**
     * Downloads the attendance report as a PDF file.
     */
    @GetMapping("/course/{courseId}/pdf")
    public ResponseEntity<byte[]> downloadPDF(
            @PathVariable Long courseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate)
            throws Exception {

        ReportDTO report = reportService.buildCourseReport(courseId, startDate, endDate);
        byte[] pdfBytes = reportService.exportToPDF(report);

        String filename = "attendance-" + report.getCourseCode() + "-"
            + startDate.format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdfBytes.length)
            .body(pdfBytes);
    }

    /**
     * Students at-risk summary (attendance below minimum threshold).
     */
    @GetMapping("/course/{courseId}/at-risk")
    public ResponseEntity<?> getAtRiskStudents(
            @PathVariable Long courseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        ReportDTO report = reportService.buildCourseReport(courseId, startDate, endDate);
        return ResponseEntity.ok(report.getStudentsAtRisk());
    }
}
