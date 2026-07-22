package in.sp.main.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.sp.main.dto.AttendanceDTO;
import in.sp.main.dto.SessionDTO;
import in.sp.main.exception.AttendanceException;
import in.sp.main.exception.ResourceNotFoundException;
import in.sp.main.model.AttendanceRecord;
import in.sp.main.model.AttendanceSession;
import in.sp.main.model.Course;
import in.sp.main.model.QRToken;
import in.sp.main.model.Student;
import in.sp.main.repository.AttendanceRecordRepository;
import in.sp.main.repository.AttendanceSessionRepository;
import in.sp.main.repository.CourseRepository;
import in.sp.main.repository.QRTokenRepository;
import in.sp.main.repository.StudentRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import in.sp.main.model.Teacher;
import in.sp.main.repository.TeacherRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository  recordRepository;
    private final CourseRepository            courseRepository;
    private final StudentRepository           studentRepository;
    private final QRTokenRepository           qrTokenRepository;
    private final QRCodeService               qrCodeService;
    private final CourseService               courseService;
    
    private final TeacherRepository teacherRepository;

    @Value("${attendance.late-threshold-minutes:15}")
    private int lateThresholdMinutes;

    // ── Session lifecycle ─────────────────────────────────────────────────

    @Transactional
    public SessionDTO startSession(Long courseId, String topic, String classRoom,
                                   AttendanceSession.AttendanceMode mode) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));

        // Close any previously active session for this course
        sessionRepository.findByCourseIdAndStatus(courseId, AttendanceSession.SessionStatus.ACTIVE)
            .ifPresent(existing -> {
                existing.setStatus(AttendanceSession.SessionStatus.CLOSED);
                existing.setEndTime(LocalDateTime.now());
                sessionRepository.save(existing);
                log.info("Auto-closed previous active session {} for course {}", existing.getId(), courseId);
            });

        LocalDateTime now = LocalDateTime.now();
        AttendanceSession session = AttendanceSession.builder()
            .course(course)
            .sessionDate(now.toLocalDate())
            .startTime(now)
            .status(AttendanceSession.SessionStatus.ACTIVE)
            .attendanceMode(mode != null ? mode : AttendanceSession.AttendanceMode.QR_CODE)
            .topic(topic)
            .classRoom(classRoom)
            .teacher(getLoggedInTeacher())
            .build();

        AttendanceSession saved = sessionRepository.save(session);
        courseService.incrementTotalClasses(courseId);
        log.info("Started session {} for course {} (mode: {})",
            saved.getId(), course.getCourseCode(), saved.getAttendanceMode());

        SessionDTO dto = toDTO(saved);

        if (saved.getAttendanceMode() == AttendanceSession.AttendanceMode.QR_CODE
                || saved.getAttendanceMode() == AttendanceSession.AttendanceMode.HYBRID) {

            SessionDTO qr = qrCodeService.generateQRForSession(saved.getId());

            dto.setQrToken(qr.getQrToken());
            dto.setQrImageBase64(qr.getQrImageBase64());
            dto.setQrExpiresInSeconds(qr.getQrExpiresInSeconds());
        }

        return dto;
    }

    @Transactional
    public SessionDTO closeSession(Long sessionId) {
        AttendanceSession session = findSessionById(sessionId);
        session.setStatus(AttendanceSession.SessionStatus.CLOSED);
        session.setEndTime(LocalDateTime.now());

        List<Student> enrolled = studentRepository.findByCourseId(session.getCourse().getId());
        int absentCount = 0;
        for (Student student : enrolled) {
            if (!recordRepository.existsBySessionIdAndStudentId(sessionId, student.getId())) {
                recordRepository.save(AttendanceRecord.builder()
                    .session(session)
                    .student(student)
                    .status(AttendanceRecord.AttendanceStatus.ABSENT)
                    .markedVia(AttendanceRecord.MarkedVia.MANUAL)
                    .markedAt(LocalDateTime.now())
                    .remarks("Auto-marked absent on session close")
                    .build());
                absentCount++;
            }
        }
        log.info("Session {} closed. Auto-absent: {}", sessionId, absentCount);
        return toDTO(sessionRepository.save(session));
    }

    // ── Mark attendance via QR scan ───────────────────────────────────────

    @Transactional
    public AttendanceDTO markAttendanceByQR(String token, String rollNumber, String clientIp) {

        // 1. Token validate kara — session + course eagerly loaded
        QRToken qrToken = qrCodeService.validateAndIncrementScan(token, clientIp);
        AttendanceSession session = qrToken.getSession();
        Long courseId = session.getCourse().getId();

        // 2. Session still ACTIVE aahe ka?
        if (session.getStatus() != AttendanceSession.SessionStatus.ACTIVE) {
            throw AttendanceException.sessionNotActive(session.getId());
        }

        // 3. Student DB madhe aahe ka?
        Student student = studentRepository.findByRollNumber(rollNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Student", "rollNumber", rollNumber));

        // 4. Student ya course madhe enrolled aahe ka?
        //    studentRepository.findByCourseId() — direct query, no lazy loading issue
        boolean enrolled = studentRepository.findByCourseId(courseId)
            .stream()
            .anyMatch(s -> s.getId().equals(student.getId()));

        if (!enrolled) {
            throw AttendanceException.studentNotEnrolled(
                rollNumber, session.getCourse().getCourseCode());
        }

        // 5. Double marking rokha
        if (recordRepository.existsBySessionIdAndStudentId(session.getId(), student.getId())) {
            throw AttendanceException.alreadyMarked(rollNumber);
        }

        // 6. PRESENT vs LATE tharva
        long minutesSinceStart = java.time.Duration
            .between(session.getStartTime(), LocalDateTime.now())
            .toMinutes();

        AttendanceRecord.AttendanceStatus status = minutesSinceStart > lateThresholdMinutes
            ? AttendanceRecord.AttendanceStatus.LATE
            : AttendanceRecord.AttendanceStatus.PRESENT;

        // 7. Record save kara
        AttendanceRecord record = AttendanceRecord.builder()
            .session(session)
            .student(student)
            .status(status)
            .markedVia(AttendanceRecord.MarkedVia.QR_CODE)
            .markedAt(LocalDateTime.now())
            .scanIp(clientIp)
            .build();

        AttendanceRecord saved = recordRepository.save(record);
        log.info("✅ QR attendance: {} → {} | session={} | ip={}",
            rollNumber, status, session.getId(), clientIp);

        return toAttendanceDTO(saved);
    }

    // ── Manual override ────────────────────────────────────────────────────

    @Transactional
    public AttendanceDTO manualMarkAttendance(Long sessionId, Long studentId,
                                              AttendanceRecord.AttendanceStatus status,
                                              String remarks) {
        AttendanceSession session = findSessionById(sessionId);
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("Student", "id", studentId));

        AttendanceRecord record = recordRepository
            .findBySessionIdAndStudentId(sessionId, studentId)
            .orElse(AttendanceRecord.builder()
                .session(session)
                .student(student)
                .build());

        record.setStatus(status);
        record.setMarkedVia(AttendanceRecord.MarkedVia.MANUAL);
        record.setMarkedAt(LocalDateTime.now());
        record.setRemarks(remarks);

        return toAttendanceDTO(recordRepository.save(record));
    }

    // ── Queries ────────────────────────────────────────────────────────────

    public List<AttendanceDTO> getSessionAttendance(Long sessionId) {
        return recordRepository.findBySessionId(sessionId)
            .stream().map(this::toAttendanceDTO).collect(Collectors.toList());
    }

    public List<AttendanceDTO> getStudentAttendance(Long studentId, Long courseId) {
        return recordRepository.findByStudentIdAndCourseId(studentId, courseId)
            .stream().map(this::toAttendanceDTO).collect(Collectors.toList());
    }

    public double getStudentAttendancePercentage(Long studentId, Long courseId) {
        long total   = sessionRepository.countCompletedSessionsByCourseId(courseId);
        long present = recordRepository.countPresentByStudentIdAndCourseId(studentId, courseId);
        if (total == 0) return 0.0;
        return Math.round((present * 100.0 / total) * 100.0) / 100.0;
    }

    public List<SessionDTO> getSessionsByCourse(Long courseId) {
        return sessionRepository.findByCourseId(courseId)
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<SessionDTO> getSessionsByDate(LocalDate date) {
        return sessionRepository.findBySessionDate(date)
            .stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public List<SessionDTO> getMySessionsByDate(LocalDate date) {

        Teacher teacher = getLoggedInTeacher();

        System.out.println("Teacher ID = " + teacher.getId());
        System.out.println("Teacher Email = " + teacher.getEmail());

        List<AttendanceSession> sessions =
                sessionRepository.findByTeacherIdAndSessionDate(
                        teacher.getId(),
                        date);

        System.out.println("Session Count = " + sessions.size());

        return sessions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 🆕 Public method — Home page sathi fakt ACTIVE sessions return karto.
     * No auth needed — students can see live sessions without logging in.
     */
    public List<SessionDTO> getActiveSessionsPublic() {
        return sessionRepository.findByStatus(AttendanceSession.SessionStatus.ACTIVE)
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private AttendanceSession findSessionById(Long id) {
        // JOIN FETCH — Course lazy load problem fix
        return sessionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Session", "id", id));
    }

    private SessionDTO toDTO(AttendanceSession s) {
        long present = recordRepository.countPresentBySessionId(s.getId());
        long total   = studentRepository.countByCourseId(s.getCourse().getId());
        double pct   = total > 0 ? (present * 100.0) / total : 0.0;

        // QR token fetch karto — Home page var student la direct scan karta yeil
        String qrToken = null;
        try {
            var qrOpt = qrTokenRepository.findBySessionId(s.getId());
            if (qrOpt.isPresent() && qrOpt.get().isValid()) {
                qrToken = qrOpt.get().getToken();
            }
        } catch (Exception ignored) { }

        return SessionDTO.builder()
            .id(s.getId())
            .courseId(s.getCourse().getId())
            .courseName(s.getCourse().getName())
            .courseCode(s.getCourse().getCourseCode())
            .sessionDate(s.getSessionDate())
            .startTime(s.getStartTime())
            .endTime(s.getEndTime())
            .status(s.getStatus())
            .attendanceMode(s.getAttendanceMode())
            .topic(s.getTopic())
            .classRoom(s.getClassRoom())
            .totalStudents((int) total)
            .presentCount((int) present)
            .absentCount((int) (total - present))
            .attendancePercentage(Math.round(pct * 100.0) / 100.0)
            .qrToken(qrToken)  // 🆕 Home page QR scan sathi
            .build();
    }

    private AttendanceDTO toAttendanceDTO(AttendanceRecord r) {
        return AttendanceDTO.builder()
            .id(r.getId())
            .sessionId(r.getSession().getId())
            .studentId(r.getStudent().getId())
            .studentName(r.getStudent().getName())
            .rollNumber(r.getStudent().getRollNumber())
            .status(r.getStatus())
            .markedVia(r.getMarkedVia())
            .markedAt(r.getMarkedAt())
            .faceConfidence(r.getFaceConfidence())
            .remarks(r.getRemarks())
            .build();
    }
    
    private Teacher getLoggedInTeacher() {

        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();

        String email = auth.getName();

        return teacherRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Teacher not found"));
    }
    
    public SessionDTO getMyActiveSession() {

        Teacher teacher = getLoggedInTeacher();

        AttendanceSession session = sessionRepository
                .findByTeacherIdAndStatus(
                        teacher.getId(),
                        AttendanceSession.SessionStatus.ACTIVE)
                .orElse(null);

        if (session == null) {
            return null;
        }

        return toDTO(session);
    }
}
