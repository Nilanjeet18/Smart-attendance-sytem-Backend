package in.sp.main.dto;

import lombok.*;

import java.time.LocalDateTime;

import in.sp.main.model.AttendanceRecord;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceDTO {

    private Long id;
    private Long sessionId;
    private Long studentId;
    private String studentName;
    private String rollNumber;
    private AttendanceRecord.AttendanceStatus status;
    private AttendanceRecord.MarkedVia markedVia;
    private LocalDateTime markedAt;
    private Double faceConfidence;
    private String remarks;

    // For QR scan requests
    private String qrToken;
    private String scanIp;

    // For face detection requests
    private String faceImageBase64;
}