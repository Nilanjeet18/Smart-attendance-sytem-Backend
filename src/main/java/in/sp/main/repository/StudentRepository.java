package in.sp.main.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import in.sp.main.model.Student;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByRollNumber(String rollNumber);

    Optional<Student> findByEmail(String email);

    List<Student> findByDepartmentAndSemester(String department, Integer semester);

    List<Student> findByIsActiveTrue();

    @Query("SELECT s FROM Student s JOIN s.courses c WHERE c.id = :courseId AND s.isActive = true")
    List<Student> findByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT s FROM Student s WHERE s.faceEncoding IS NOT NULL AND s.isActive = true")
    List<Student> findAllWithFaceEncodings();

    boolean existsByRollNumber(String rollNumber);

    boolean existsByEmail(String email);

    @Query("SELECT COUNT(s) FROM Student s JOIN s.courses c WHERE c.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);
}
