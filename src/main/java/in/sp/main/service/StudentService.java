package in.sp.main.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.sp.main.dto.StudentDTO;
import in.sp.main.exception.AttendanceException;
import in.sp.main.exception.ResourceNotFoundException;
import in.sp.main.model.Student;
import in.sp.main.repository.StudentRepository;
import in.sp.main.util.FaceDetectionUtil;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final FaceDetectionUtil faceDetectionUtil;

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public StudentDTO createStudent(StudentDTO dto) {
        if (studentRepository.existsByRollNumber(dto.getRollNumber())) {
            throw new AttendanceException("Roll number already exists: " + dto.getRollNumber(),
                "DUPLICATE_ROLL");
        }
        if (studentRepository.existsByEmail(dto.getEmail())) {
            throw new AttendanceException("Email already registered: " + dto.getEmail(),
                "DUPLICATE_EMAIL");
        }
        Student student = toEntity(dto);
        Student saved = studentRepository.save(student);
        log.info("Created student: {} ({})", saved.getName(), saved.getRollNumber());
        return toDTO(saved);
    }

    public StudentDTO getStudentById(Long id) {
        return toDTO(findById(id));
    }

    public StudentDTO getStudentByRollNumber(String rollNumber) {
        Student student = studentRepository.findByRollNumber(rollNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Student", "rollNumber", rollNumber));
        return toDTO(student);
    }

    public List<StudentDTO> getAllStudents() {
        return studentRepository.findByIsActiveTrue()
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<StudentDTO> getStudentsByCourse(Long courseId) {
        return studentRepository.findByCourseId(courseId)
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public StudentDTO updateStudent(Long id, StudentDTO dto) {
        Student existing = findById(id);
        existing.setName(dto.getName());
        existing.setEmail(dto.getEmail());
        existing.setPhoneNumber(dto.getPhoneNumber());
        existing.setDepartment(dto.getDepartment());
        existing.setSemester(dto.getSemester());
        if (dto.getIsActive() != null) {
            existing.setIsActive(dto.getIsActive());
        }
        return toDTO(studentRepository.save(existing));
    }

    @Transactional
    public void deleteStudent(Long id) {
        Student student = findById(id);
        student.setIsActive(false);  // Soft delete
        studentRepository.save(student);
        log.info("Soft-deleted student id={}", id);
    }

    // ── Face Registration ─────────────────────────────────────────────────

    @Transactional
    public StudentDTO registerFace(Long studentId, String faceImageBase64) {
        Student student = findById(studentId);

        log.info("Extracting face encoding for student: {}", student.getRollNumber());
        String encoding = faceDetectionUtil.extractFaceEncoding(faceImageBase64);

        student.setFaceEncoding(encoding);
        Student saved = studentRepository.save(student);
        log.info("Face registered successfully for student: {}", saved.getRollNumber());
        return toDTO(saved);
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    public Student findById(Long id) {
        return studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Student", "id", id));
    }

    public Student findByRollNumber(String rollNumber) {
        return studentRepository.findByRollNumber(rollNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Student", "rollNumber", rollNumber));
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    public StudentDTO toDTO(Student s) {
        return StudentDTO.builder()
            .id(s.getId())
            .name(s.getName())
            .rollNumber(s.getRollNumber())
            .email(s.getEmail())
            .phoneNumber(s.getPhoneNumber())
            .department(s.getDepartment())
            .semester(s.getSemester())
            .isActive(s.getIsActive())
            .profileImagePath(s.getProfileImagePath())
            .hasFaceEncoding(s.getFaceEncoding() != null)
            .build();
    }

    private Student toEntity(StudentDTO dto) {
        return Student.builder()
            .name(dto.getName())
            .rollNumber(dto.getRollNumber())
            .email(dto.getEmail())
            .phoneNumber(dto.getPhoneNumber())
            .department(dto.getDepartment())
            .semester(dto.getSemester())
            .isActive(true)
            .build();
    }
}