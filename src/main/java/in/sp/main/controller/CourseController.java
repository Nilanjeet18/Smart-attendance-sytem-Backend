package in.sp.main.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import in.sp.main.model.Course;
import in.sp.main.service.CourseService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Course> create(
            @RequestBody Course course,
            @RequestParam Long teacherId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(courseService.createCourse(course, teacherId));
    }

    @GetMapping
    public ResponseEntity<List<Course>> getAll() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Course> getById(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<List<Course>> getByTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(courseService.getCoursesByTeacher(teacherId));
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<Course>> getByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(courseService.getCoursesByStudent(studentId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Course> update(@PathVariable Long id, @RequestBody Course course) {
        return ResponseEntity.ok(courseService.updateCourse(id, course));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return ResponseEntity.ok(Map.of("message", "Course deactivated"));
    }

    // ── Enrollment ────────────────────────────────────────────────────────

    @PostMapping("/{courseId}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Course> enrollStudent(
            @PathVariable Long courseId, @PathVariable Long studentId) {
        return ResponseEntity.ok(courseService.enrollStudent(courseId, studentId));
    }

    @DeleteMapping("/{courseId}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Course> unenrollStudent(
            @PathVariable Long courseId, @PathVariable Long studentId) {
        return ResponseEntity.ok(courseService.unenrollStudent(courseId, studentId));
    }

    @PostMapping("/{courseId}/students/bulk")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, String>> enrollBulk(
            @PathVariable Long courseId, @RequestBody List<Long> studentIds) {
        courseService.enrollStudents(courseId, studentIds);
        return ResponseEntity.ok(Map.of("message",
            "Enrolled " + studentIds.size() + " students"));
    }
}
