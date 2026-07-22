package in.sp.main.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import in.sp.main.dto.ReportDTO;
import in.sp.main.model.AttendanceRecord;
import in.sp.main.model.AttendanceSession;
import in.sp.main.model.Course;
import in.sp.main.model.Student;
import in.sp.main.repository.AttendanceRecordRepository;
import in.sp.main.repository.AttendanceSessionRepository;
import in.sp.main.repository.CourseRepository;
import in.sp.main.repository.StudentRepository;
import com.itextpdf.text.Font;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AttendanceRecordRepository  recordRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final StudentRepository           studentRepository;
    private final CourseRepository            courseRepository;

    @Value("${attendance.min-percentage:75.0}")
    private double minAttendancePercentage;

    @Value("${attendance.report.output-dir:./reports/}")
    private String reportOutputDir;

    // ── Build course attendance report ─────────────────────────────────────

    public ReportDTO buildCourseReport(Long courseId, LocalDate startDate, LocalDate endDate) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("Course not found: " + courseId));

        List<AttendanceSession> sessions = sessionRepository
            .findByCourseIdAndDateRange(courseId, startDate, endDate);

        List<Student> students = studentRepository.findByCourseId(courseId);
        int totalSessions = sessions.size();

        List<ReportDTO.StudentAttendanceSummary> summaries = students.stream().map(student -> {
            List<AttendanceRecord> records = recordRepository
                .findByStudentIdAndCourseId(student.getId(), courseId);

            Map<String, String> dateWise = new LinkedHashMap<>();
            for (AttendanceSession s : sessions) {
                String dateKey = s.getSessionDate().toString();
                records.stream()
                    .filter(r -> r.getSession().getId().equals(s.getId()))
                    .findFirst()
                    .ifPresentOrElse(
                        r -> dateWise.put(dateKey, r.getStatus().name()),
                        () -> dateWise.put(dateKey, "ABSENT")
                    );
            }

            long presentCount = records.stream()
                .filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT
                          || r.getStatus() == AttendanceRecord.AttendanceStatus.LATE)
                .count();

            double pct = totalSessions > 0
                ? (presentCount * 100.0) / totalSessions : 0.0;

            return ReportDTO.StudentAttendanceSummary.builder()
                .studentId(student.getId())
                .studentName(student.getName())
                .rollNumber(student.getRollNumber())
                .classesAttended((int) presentCount)
                .totalClasses(totalSessions)
                .attendancePercentage(Math.round(pct * 100.0) / 100.0)
                .isAtRisk(pct < minAttendancePercentage)
                .dateWiseStatus(dateWise)
                .build();
        }).collect(Collectors.toList());

        double avgAttendance = summaries.stream()
            .mapToDouble(ReportDTO.StudentAttendanceSummary::getAttendancePercentage)
            .average().orElse(0.0);

        List<ReportDTO.DailyAttendanceSummary> dailySummaries = sessions.stream().map(s -> {
            long present = recordRepository.countPresentBySessionId(s.getId());
            double pct = students.size() > 0
                ? (present * 100.0) / students.size() : 0.0;
            return ReportDTO.DailyAttendanceSummary.builder()
                .date(s.getSessionDate())
                .sessionId(s.getId())
                .topic(s.getTopic())
                .presentCount((int) present)
                .totalCount(students.size())
                .percentage(Math.round(pct * 100.0) / 100.0)
                .build();
        }).collect(Collectors.toList());

        List<ReportDTO.StudentAttendanceSummary> atRisk = summaries.stream()
            .filter(ReportDTO.StudentAttendanceSummary::getIsAtRisk)
            .collect(Collectors.toList());

        return ReportDTO.builder()
            .courseId(courseId)
            .courseName(course.getName())
            .courseCode(course.getCourseCode())
            .startDate(startDate)
            .endDate(endDate)
            .totalSessions(totalSessions)
            .totalStudents(students.size())
            .averageAttendance(Math.round(avgAttendance * 100.0) / 100.0)
            .studentSummaries(summaries)
            .dailySummaries(dailySummaries)
            .studentsAtRisk(atRisk)
            .minimumRequiredPercentage(minAttendancePercentage)
            .build();
    }

    // ── Export as Excel ───────────────────────────────────────────────────

    public byte[] exportToExcel(ReportDTO report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Attendance Report");

            // Header styles
            CellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle atRiskStyle = workbook.createCellStyle();
            atRiskStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            atRiskStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Row 0: Title
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Attendance Report — " + report.getCourseName()
                + " (" + report.getCourseCode() + ")");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

            // Row 1: Meta
            Row metaRow = sheet.createRow(1);
            metaRow.createCell(0).setCellValue("Period: " + report.getStartDate()
                + "  to  " + report.getEndDate());
            metaRow.createCell(4).setCellValue("Min Required: " + report.getMinimumRequiredPercentage() + "%");

            // Row 3: Column headers
            Row headerRow = sheet.createRow(3);
            String[] headers = {"Roll No.", "Name", "Classes Attended",
                "Total Classes", "Percentage", "Status"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // Data rows
            int rowNum = 4;
            for (ReportDTO.StudentAttendanceSummary s : report.getStudentSummaries()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(s.getRollNumber());
                row.createCell(1).setCellValue(s.getStudentName());
                row.createCell(2).setCellValue(s.getClassesAttended());
                row.createCell(3).setCellValue(s.getTotalClasses());
                row.createCell(4).setCellValue(s.getAttendancePercentage() + "%");
                Cell statusCell = row.createCell(5);
                statusCell.setCellValue(s.getIsAtRisk() ? "⚠ AT RISK" : "✓ OK");
                if (s.getIsAtRisk()) {
                    for (int c = 0; c <= 5; c++) {
                        row.getCell(c).setCellStyle(atRiskStyle);
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            log.info("Excel report generated: {} rows", rowNum - 4);
            return out.toByteArray();
        }
    }

    // ── Export as PDF ─────────────────────────────────────────────────────

    public byte[] exportToPDF(ReportDTO report) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4.rotate());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();

        // Title
        Font titleFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, BaseColor.BLACK);

        Paragraph title = new Paragraph(
            "Attendance Report - " + report.getCourseName()
            + " (" + report.getCourseCode() + ")",
            titleFont
        );

        title.setAlignment(Element.ALIGN_CENTER);

        document.add(title);
        document.add(Chunk.NEWLINE);

        // Summary
        Font normalFont = new Font(Font.FontFamily.HELVETICA, 10);
        document.add(new Paragraph("Period: " + report.getStartDate()
            + " to " + report.getEndDate(), normalFont));
        document.add(new Paragraph("Total Sessions: " + report.getTotalSessions()
            + "   |   Average Attendance: " + report.getAverageAttendance() + "%"
            + "   |   At Risk: " + report.getStudentsAtRisk().size() + " students", normalFont));
        document.add(Chunk.NEWLINE);

        // Table
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2f, 4f, 2f, 2f, 2f});

        BaseColor headerBg = new BaseColor(70, 130, 180);
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
        for (String h : List.of("Roll No.", "Name", "Attended", "Total", "Percentage")) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setPadding(6);
            table.addCell(cell);
        }

        Font cellFont   = new Font(Font.FontFamily.HELVETICA, 9);
        BaseColor atRiskBg = new BaseColor(255, 200, 200);

        for (ReportDTO.StudentAttendanceSummary s : report.getStudentSummaries()) {
            BaseColor bg = s.getIsAtRisk() ? atRiskBg : BaseColor.WHITE;
            for (String value : List.of(
                    s.getRollNumber(),
                    s.getStudentName(),
                    String.valueOf(s.getClassesAttended()),
                    String.valueOf(s.getTotalClasses()),
                    s.getAttendancePercentage() + "%")) {
                PdfPCell cell = new PdfPCell(new Phrase(value, cellFont));
                cell.setBackgroundColor(bg);
                cell.setPadding(5);
                table.addCell(cell);
            }
        }

        document.add(table);
        document.close();
        log.info("PDF report generated for course: {}", report.getCourseCode());
        return out.toByteArray();
    }

    // ── Scheduled daily report — runs at 11:59 PM every day ──────────────

    @Scheduled(cron = "0 59 23 * * *")
    public void generateDailyReport() {
        LocalDate today = LocalDate.now();
        log.info("Generating scheduled daily attendance report for {}", today);
        List<AttendanceSession> sessions = sessionRepository.findBySessionDate(today);

        String filename = reportOutputDir + "daily-report-"
            + today.format(DateTimeFormatter.BASIC_ISO_DATE) + ".txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("=== Daily Attendance Report: " + today + " ===");
            writer.println("Total sessions held: " + sessions.size());
            for (AttendanceSession s : sessions) {
                long present = recordRepository.countPresentBySessionId(s.getId());
                long total   = studentRepository.countByCourseId(s.getCourse().getId());
                writer.printf("  Course: %-20s | Session: %d | Present: %d/%d%n",
                    s.getCourse().getCourseCode(), s.getId(), present, total);
            }
            writer.println("===========================================");
            log.info("Daily report saved to: {}", filename);
        } catch (IOException e) {
            log.error("Failed to write daily report: {}", e.getMessage(), e);
        }
    }
}
