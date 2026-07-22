package in.sp.main.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import in.sp.main.dto.AttendanceDTO;
import in.sp.main.dto.SessionDTO;
import in.sp.main.model.AttendanceRecord;
import in.sp.main.model.AttendanceSession;
import in.sp.main.service.AttendanceService;
import in.sp.main.service.FaceDetectionService;
import in.sp.main.service.QRCodeService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService    attendanceService;
    private final QRCodeService        qrCodeService;
    private final FaceDetectionService faceDetectionService;

    // ── Session management ────────────────────────────────────────────────

    @PostMapping("/sessions/start")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<SessionDTO> startSession(
            @RequestParam Long courseId,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String classRoom,
            @RequestParam(required = false, defaultValue = "QR_CODE")
                AttendanceSession.AttendanceMode mode) {
        return ResponseEntity.ok(
            attendanceService.startSession(courseId, topic, classRoom, mode));
    }

    @PutMapping("/sessions/{sessionId}/close")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<SessionDTO> closeSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(attendanceService.closeSession(sessionId));
    }

    @GetMapping("/sessions/course/{courseId}")
    public ResponseEntity<List<SessionDTO>> getSessionsByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(attendanceService.getSessionsByCourse(courseId));
    }

    /**
     * 🆕 PUBLIC endpoint — Home page var aaj che sessions dakhavto.
     * Students la token lagat nahi — fakt today's sessions list milte.
     * SecurityConfig madhe "/api/attendance/sessions/date/**" permit aahe.
     */
    @GetMapping("/sessions/date/{date}")
    public ResponseEntity<List<SessionDTO>> getSessionsByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceService.getSessionsByDate(date));
    }
    
    @GetMapping("/sessions/my/date/{date}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<List<SessionDTO>> getMySessionsByDate(
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        System.out.println("====== MY SESSION API CALLED ======");

        return ResponseEntity.ok(
                attendanceService.getMySessionsByDate(date));
    }

    /**
     * 🆕 PUBLIC endpoint — Fakt ACTIVE sessions return karto.
     * Home page var "Live Now" section sathi use hoto.
     * GET /api/attendance/sessions/active
     */
    @GetMapping("/sessions/active")
    public ResponseEntity<List<SessionDTO>> getActiveSessions() {
        return ResponseEntity.ok(attendanceService.getActiveSessionsPublic());
    }

    // ── QR Code attendance ────────────────────────────────────────────────

    @PostMapping("/qr/scan")
    public ResponseEntity<AttendanceDTO> markByQR(
            @RequestParam String token,
            @RequestParam String rollNumber,
            HttpServletRequest request) {
        String clientIp = getClientIp(request);
        return ResponseEntity.ok(
            attendanceService.markAttendanceByQR(token, rollNumber, clientIp));
    }

    @PostMapping("/sessions/{sessionId}/qr/generate")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<SessionDTO> generateQR(@PathVariable Long sessionId) {
        return ResponseEntity.ok(qrCodeService.generateQRForSession(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/qr/refresh")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<SessionDTO> refreshQR(@PathVariable Long sessionId) {
        return ResponseEntity.ok(qrCodeService.refreshQR(sessionId));
    }

    // ── Face detection attendance ─────────────────────────────────────────

    @PostMapping("/face/scan")
    public ResponseEntity<AttendanceDTO> markByFace(
            @RequestParam Long sessionId,
            @RequestBody Map<String, String> body) {
        String imageBase64 = body.get("faceImageBase64");
        return ResponseEntity.ok(
            faceDetectionService.markAttendanceByFace(sessionId, imageBase64));
    }

    @PostMapping("/face/detect")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> detectFaces(@RequestBody Map<String, String> body) {
        var regions = faceDetectionService.detectFacesOnly(body.get("imageBase64"));
        return ResponseEntity.ok(Map.of(
            "faceCount", regions.size(),
            "faces", regions
        ));
    }

    // ── Manual override ───────────────────────────────────────────────────

    @PostMapping("/sessions/{sessionId}/manual")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<AttendanceDTO> manualMark(
            @PathVariable Long sessionId,
            @RequestParam Long studentId,
            @RequestParam(defaultValue = "PRESENT") AttendanceRecord.AttendanceStatus status,
            @RequestParam(required = false) String remarks) {
        return ResponseEntity.ok(
            attendanceService.manualMarkAttendance(sessionId, studentId, status, remarks));
    }

    // ── Records query ─────────────────────────────────────────────────────

    @GetMapping("/sessions/{sessionId}/records")
    public ResponseEntity<List<AttendanceDTO>> getSessionRecords(@PathVariable Long sessionId) {
        return ResponseEntity.ok(attendanceService.getSessionAttendance(sessionId));
    }

    // ── Util ──────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return (xfHeader != null) ? xfHeader.split(",")[0].trim()
                                  : request.getRemoteAddr();
    }
    
    @GetMapping("/session/active")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<SessionDTO> getMyActiveSession() {

        SessionDTO dto = attendanceService.getMyActiveSession();

        if (dto == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(dto);
    }
}
