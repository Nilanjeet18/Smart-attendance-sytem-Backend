package in.sp.main.service;

import in.sp.main.dto.TeacherDTO;
import in.sp.main.exception.AttendanceException;
import in.sp.main.model.Teacher;
import in.sp.main.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherServiceImpl implements TeacherService {

    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public TeacherDTO addTeacher(TeacherDTO dto) {

        if (teacherRepository.existsByEmail(dto.getEmail())) {
            throw new AttendanceException("Email already exists", "DUPLICATE_EMAIL");
        }

        if (teacherRepository.existsByEmployeeId(dto.getEmployeeId())) {
            throw new AttendanceException("Employee ID already exists", "DUPLICATE_EMPLOYEE_ID");
        }

        Teacher teacher = Teacher.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .employeeId(dto.getEmployeeId())
                .department(dto.getDepartment())
                .phoneNumber(dto.getPhoneNumber())
                .role(dto.getRole())
                .isActive(true)
                .build();

        Teacher saved = teacherRepository.save(teacher);

        return mapToDTO(saved);
    }

    @Override
    public List<TeacherDTO> getAllTeachers() {

        return teacherRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public TeacherDTO getTeacherById(Long id) {

        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() ->
                        new AttendanceException("Teacher not found", "NOT_FOUND"));

        return mapToDTO(teacher);
    }

    @Override
    public TeacherDTO updateTeacher(Long id, TeacherDTO dto) {

        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() ->
                        new AttendanceException("Teacher not found", "NOT_FOUND"));

        // Email Duplicate Check
        Teacher emailTeacher = teacherRepository.findByEmail(dto.getEmail()).orElse(null);

        if (emailTeacher != null && !emailTeacher.getId().equals(id)) {
            throw new AttendanceException("Email already exists", "DUPLICATE_EMAIL");
        }

        // Employee ID Duplicate Check
        Teacher employeeTeacher = teacherRepository.findByEmployeeId(dto.getEmployeeId()).orElse(null);

        if (employeeTeacher != null && !employeeTeacher.getId().equals(id)) {
            throw new AttendanceException("Employee ID already exists", "DUPLICATE_EMPLOYEE_ID");
        }

        teacher.setName(dto.getName());
        teacher.setEmail(dto.getEmail());
        teacher.setEmployeeId(dto.getEmployeeId());
        teacher.setDepartment(dto.getDepartment());
        teacher.setPhoneNumber(dto.getPhoneNumber());
        teacher.setRole(dto.getRole());

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            teacher.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        Teacher updated = teacherRepository.save(teacher);

        return mapToDTO(updated);
    }

    @Override
    public void deleteTeacher(Long id) {

        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() ->
                        new AttendanceException("Teacher not found", "NOT_FOUND"));

        // Soft Delete
        teacher.setIsActive(false);

        teacherRepository.save(teacher);
       
    }

    private TeacherDTO mapToDTO(Teacher teacher) {

        return TeacherDTO.builder()
                .id(teacher.getId())
                .name(teacher.getName())
                .email(teacher.getEmail())
                .employeeId(teacher.getEmployeeId())
                .department(teacher.getDepartment())
                .phoneNumber(teacher.getPhoneNumber())
                .role(teacher.getRole())
                .isActive(teacher.getIsActive())
                .build();
    }
}