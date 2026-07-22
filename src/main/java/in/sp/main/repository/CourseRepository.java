package in.sp.main.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import in.sp.main.model.AttendanceSession;
import in.sp.main.model.Course;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCourseCode(String courseCode);

    List<Course> findByTeacherId(Long teacherId);

    List<Course> findByDepartmentAndSemester(String department, Integer semester);

    @EntityGraph(attributePaths = {
            "students",
            "teacher"
    })
    List<Course> findByIsActiveTrue();

    @Query("SELECT c FROM Course c JOIN c.students s WHERE s.id = :studentId AND c.isActive = true")
    List<Course> findByStudentId(@Param("studentId") Long studentId);

    boolean existsByCourseCode(String courseCode);
}