package in.sp.main.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportDTO {

    // Course-level report
    private Long courseId;
    private String courseName;
    private String courseCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalSessions;
    private Integer totalStudents;
    private Double averageAttendance;

    // Student-wise breakdown
    private List<StudentAttendanceSummary> studentSummaries;

    // Date-wise breakdown
    private List<DailyAttendanceSummary> dailySummaries;

    // Students below threshold
    private List<StudentAttendanceSummary> studentsAtRisk;
    private Double minimumRequiredPercentage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentAttendanceSummary {
        private Long studentId;
        private String studentName;
        private String rollNumber;
        private Integer classesAttended;
        private Integer totalClasses;
        private Double attendancePercentage;
        private Boolean isAtRisk;
        private Map<String, String> dateWiseStatus; // date -> PRESENT/ABSENT/LATE
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyAttendanceSummary {
        private LocalDate date;
        private Long sessionId;
        private String topic;
        private Integer presentCount;
        private Integer totalCount;
        private Double percentage;
    }
}
