package in.sp.main.service;

import java.util.List;

import in.sp.main.dto.TeacherDTO;

public interface TeacherService {

    TeacherDTO addTeacher(TeacherDTO teacherDTO);

    List<TeacherDTO> getAllTeachers();

    TeacherDTO getTeacherById(Long id);

    TeacherDTO updateTeacher(Long id, TeacherDTO teacherDTO);

    void deleteTeacher(Long id);

}