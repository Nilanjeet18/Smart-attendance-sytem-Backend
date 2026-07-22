package in.sp.main.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "attendance_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_mode")
    @Builder.Default
    private AttendanceMode attendanceMode = AttendanceMode.QR_CODE;

    // Topic or lecture title for this session
    @Column(name = "topic")
    private String topic;

    @Column(name = "class_room")
    private String classRoom;

    @OneToOne(mappedBy = "session", cascade = CascadeType.ALL)
    private QRToken qrToken;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL)
    private List<AttendanceRecord> attendanceRecords;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @ManyToOne
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum SessionStatus {
        ACTIVE, CLOSED, CANCELLED
    }

    public enum AttendanceMode {
        QR_CODE, FACE_DETECTION, MANUAL, HYBRID
    }
}
