package in.sp.main.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "attendance_records",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "student_id"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "marked_via")
    private MarkedVia markedVia;

    @Column(name = "marked_at")
    private LocalDateTime markedAt;

    // Confidence score from face detection (0.0 to 1.0)
    @Column(name = "face_confidence")
    private Double faceConfidence;

    @Column(name = "remarks")
    private String remarks;

    // IP address from which QR scan was done
    @Column(name = "scan_ip")
    private String scanIp;

    @PrePersist
    protected void onCreate() {
        if (markedAt == null) {
            markedAt = LocalDateTime.now();
        }
    }

    public enum AttendanceStatus {
        PRESENT, ABSENT, LATE, EXCUSED
    }

    public enum MarkedVia {
        QR_CODE, FACE_DETECTION, MANUAL
    }
}