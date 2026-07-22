package in.sp.main.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.sp.main.dto.SessionDTO;
import in.sp.main.exception.AttendanceException;
import in.sp.main.exception.ResourceNotFoundException;
import in.sp.main.model.Course;
import in.sp.main.model.Student;
import in.sp.main.model.Teacher;
import in.sp.main.repository.CourseRepository;
import in.sp.main.repository.StudentRepository;
import in.sp.main.repository.TeacherRepository;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository   courseRepository;
    private final TeacherRepository  teacherRepository;
    private final StudentRepository  studentRepository;

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public Course createCourse(Course course, Long teacherId) {

        try {

            if (courseRepository.existsByCourseCode(course.getCourseCode())) {
                throw new AttendanceException(
                        "Course code already exists",
                        "DUPLICATE_COURSE_CODE");
            }

            Teacher teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Teacher", "id", teacherId));

            course.setTeacher(teacher);

            Course saved = courseRepository.save(course);

            return saved;

        } catch (Exception e) {

            e.printStackTrace();

            throw e;
        }
    }

    public Course getCourseById(Long id) {
        return courseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
    }

    public List<Course> getAllCourses() {
        return courseRepository.findByIsActiveTrue();	
    }

    public List<Course> getCoursesByTeacher(Long teacherId) {
        return courseRepository.findByTeacherId(teacherId);
    }

    public List<Course> getCoursesByStudent(Long studentId) {
        return courseRepository.findByStudentId(studentId);
    }

    @Transactional
    public Course updateCourse(Long id, Course updated) {
        Course existing = getCourseById(id);

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setSemester(updated.getSemester());
        existing.setDepartment(updated.getDepartment());

        return courseRepository.save(existing);
    }

    @Transactional
    public void deleteCourse(Long id) {
        Course course = getCourseById(id);
        course.setIsActive(false);
        courseRepository.save(course);
    }

    // ── Enrollment ────────────────────────────────────────────────────────

    @Transactional
    public Course enrollStudent(Long courseId, Long studentId) {
        Course  course  = getCourseById(courseId);
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("Student", "id", studentId));

        boolean alreadyEnrolled = course.getStudents() != null
            && course.getStudents().stream().anyMatch(s -> s.getId().equals(studentId));

        if (alreadyEnrolled) {
            throw new AttendanceException(
                "Student already enrolled in this course", "ALREADY_ENROLLED");
        }

        course.getStudents().add(student);
        Course saved = courseRepository.save(course);
        log.info("Enrolled student {} in course {}", student.getRollNumber(), course.getCourseCode());
        return saved;
    }

    @Transactional
    public Course unenrollStudent(Long courseId, Long studentId) {
        Course course = getCourseById(courseId);
        if (course.getStudents() != null) {
            course.getStudents().removeIf(s -> s.getId().equals(studentId));
        }
        return courseRepository.save(course);
    }

    @Transactional
    public void enrollStudents(Long courseId, List<Long> studentIds) {
        Course course = getCourseById(courseId);
        List<Student> students = studentRepository.findAllById(studentIds);
        course.getStudents().addAll(students);
        courseRepository.save(course);
        log.info("Bulk-enrolled {} students into course {}", students.size(), course.getCourseCode());
    }

    // ── Increment total class count ───────────────────────────────────────

    @Transactional
    public void incrementTotalClasses(Long courseId) {
        Course course = getCourseById(courseId);
        course.setTotalClasses(course.getTotalClasses() + 1);
        courseRepository.save(course);
    }
}
