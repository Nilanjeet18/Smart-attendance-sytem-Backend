package in.sp.main.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import in.sp.main.model.AttendanceSession;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionDTO {

    private Long id;
    private Long courseId;
    private String courseName;
    private String courseCode;
    private LocalDate sessionDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AttendanceSession.SessionStatus status;
    private AttendanceSession.AttendanceMode attendanceMode;
    private String topic;
    private String classRoom;
    private String qrToken;
    private String qrImageBase64;
    private Long qrExpiresInSeconds;

    // Summary stats
    private Integer totalStudents;
    private Integer presentCount;
    private Integer absentCount;
    private Double attendancePercentage;
}