package in.sp.main.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import in.sp.main.dto.AttendanceDTO;
import in.sp.main.dto.StudentDTO;
import in.sp.main.service.AttendanceService;
import in.sp.main.service.StudentService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService    studentService;
    private final AttendanceService attendanceService;

    // ── CRUD ──────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<StudentDTO> create(@Valid @RequestBody StudentDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.createStudent(dto));
    }

    @GetMapping
    public ResponseEntity<List<StudentDTO>> getAll() {
        return ResponseEntity.ok(studentService.getAllStudents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getStudentById(id));
    }

    @GetMapping("/roll/{rollNumber}")
    public ResponseEntity<StudentDTO> getByRoll(@PathVariable String rollNumber) {
        return ResponseEntity.ok(studentService.getStudentByRollNumber(rollNumber));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<StudentDTO>> getByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(studentService.getStudentsByCourse(courseId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<StudentDTO> update(@PathVariable Long id,
                                             @Valid @RequestBody StudentDTO dto) {
        return ResponseEntity.ok(studentService.updateStudent(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return ResponseEntity.ok(Map.of("message", "Student deactivated successfully"));
    }

    // ── Face Registration ─────────────────────────────────────────────────

    @PostMapping("/{id}/face/register")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<StudentDTO> registerFace(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String imageBase64 = body.get("faceImageBase64");
        if (imageBase64 == null || imageBase64.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(studentService.registerFace(id, imageBase64));
    }

    // ── Attendance history ────────────────────────────────────────────────

    @GetMapping("/{id}/attendance/{courseId}")
    public ResponseEntity<List<AttendanceDTO>> getAttendance(
            @PathVariable Long id, @PathVariable Long courseId) {
        return ResponseEntity.ok(attendanceService.getStudentAttendance(id, courseId));
    }

    @GetMapping("/{id}/attendance/{courseId}/percentage")
    public ResponseEntity<Map<String, Object>> getAttendancePercentage(
            @PathVariable Long id, @PathVariable Long courseId) {
        double pct = attendanceService.getStudentAttendancePercentage(id, courseId);
        return ResponseEntity.ok(Map.of(
            "studentId", id,
            "courseId", courseId,
            "percentage", pct,
            "status", pct >= 75.0 ? "SAFE" : "AT_RISK"
        ));
    }
}