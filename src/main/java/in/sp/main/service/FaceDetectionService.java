package in.sp.main.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.sp.main.dto.AttendanceDTO;
import in.sp.main.exception.AttendanceException;
import in.sp.main.model.AttendanceRecord;
import in.sp.main.model.AttendanceSession;
import in.sp.main.model.Student;
import in.sp.main.repository.AttendanceRecordRepository;
import in.sp.main.repository.AttendanceSessionRepository;
import in.sp.main.repository.StudentRepository;
import in.sp.main.util.FaceDetectionUtil;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceDetectionService {

    private final FaceDetectionUtil            faceDetectionUtil;
    private final StudentRepository            studentRepository;
    private final AttendanceSessionRepository  sessionRepository;
    private final AttendanceRecordRepository   recordRepository;

    // ── Mark attendance via face scan ─────────────────────────────────────

    @Transactional
    public AttendanceDTO markAttendanceByFace(Long sessionId, String faceImageBase64) {

        // 1. Validate session
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new AttendanceException("Session not found: " + sessionId));

        if (session.getStatus() != AttendanceSession.SessionStatus.ACTIVE) {
            throw AttendanceException.sessionNotActive(sessionId);
        }

        // 2. Detect faces in provided image
        List<FaceDetectionUtil.FaceRegion> detectedFaces =
            faceDetectionUtil.detectFaces(faceImageBase64);

        if (detectedFaces.isEmpty()) {
            throw AttendanceException.noFaceDetected();
        }
        if (detectedFaces.size() > 1) {
            throw new AttendanceException(
                "Multiple faces detected. Please scan one person at a time.", "MULTIPLE_FACES");
        }

        // 3. Extract encoding from captured image
        String capturedEncoding = faceDetectionUtil.extractFaceEncoding(faceImageBase64);

        // 4. Match against all enrolled students with registered faces
        List<Student> enrolledStudents = studentRepository.findByCourseId(session.getCourse().getId());
        List<Student> studentsWithFace = enrolledStudents.stream()
            .filter(s -> s.getFaceEncoding() != null && s.getIsActive())
            .toList();

        if (studentsWithFace.isEmpty()) {
            throw new AttendanceException(
                "No students have registered faces for this course", "NO_FACE_REGISTRATIONS");
        }

        Student matchedStudent = null;
        double  bestScore      = 0.0;

        for (Student candidate : studentsWithFace) {
            double score = faceDetectionUtil.compareFaceEncodings(
                capturedEncoding, candidate.getFaceEncoding());
            log.debug("Face match score for {}: {}", candidate.getRollNumber(), score);
            if (score > bestScore) {
                bestScore = score;
                matchedStudent = candidate;
            }
        }

        // 5. Check against confidence threshold
        if (matchedStudent == null || !faceDetectionUtil.isSamePerson(
                capturedEncoding, matchedStudent.getFaceEncoding())) {
            throw AttendanceException.faceNotRecognized(bestScore);
        }

        // 6. Prevent double marking
        if (recordRepository.existsBySessionIdAndStudentId(sessionId, matchedStudent.getId())) {
            throw AttendanceException.alreadyMarked(matchedStudent.getRollNumber());
        }

        // 7. Save attendance record
        AttendanceRecord record = AttendanceRecord.builder()
            .session(session)
            .student(matchedStudent)
            .status(AttendanceRecord.AttendanceStatus.PRESENT)
            .markedVia(AttendanceRecord.MarkedVia.FACE_DETECTION)
            .markedAt(LocalDateTime.now())
            .faceConfidence(bestScore)
            .build();

        AttendanceRecord saved = recordRepository.save(record);
        log.info("Face attendance marked: {} in session {} (confidence: {})",
            matchedStudent.getRollNumber(), sessionId, bestScore);

        return AttendanceDTO.builder()
            .id(saved.getId())
            .sessionId(sessionId)
            .studentId(matchedStudent.getId())
            .studentName(matchedStudent.getName())
            .rollNumber(matchedStudent.getRollNumber())
            .status(AttendanceRecord.AttendanceStatus.PRESENT)
            .markedVia(AttendanceRecord.MarkedVia.FACE_DETECTION)
            .markedAt(saved.getMarkedAt())
            .faceConfidence(bestScore)
            .build();
    }

    // ── Detect only — no attendance marking ───────────────────────────────

    public List<FaceDetectionUtil.FaceRegion> detectFacesOnly(String imageBase64) {
        return faceDetectionUtil.detectFaces(imageBase64);
    }
}
