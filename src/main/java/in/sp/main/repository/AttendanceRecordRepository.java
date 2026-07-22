package in.sp.main.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import in.sp.main.model.AttendanceRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    List<AttendanceRecord> findBySessionId(Long sessionId);

    List<AttendanceRecord> findByStudentId(Long studentId);

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    @Query("SELECT ar FROM AttendanceRecord ar WHERE ar.student.id = :studentId " +
           "AND ar.session.course.id = :courseId")
    List<AttendanceRecord> findByStudentIdAndCourseId(
        @Param("studentId") Long studentId,
        @Param("courseId") Long courseId);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar WHERE ar.student.id = :studentId " +
           "AND ar.session.course.id = :courseId AND ar.status = 'PRESENT'")
    long countPresentByStudentIdAndCourseId(
        @Param("studentId") Long studentId,
        @Param("courseId") Long courseId);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar WHERE ar.session.id = :sessionId " +
           "AND ar.status = 'PRESENT'")
    long countPresentBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT ar FROM AttendanceRecord ar " +
           "JOIN ar.session s WHERE s.course.id = :courseId " +
           "AND s.sessionDate BETWEEN :startDate AND :endDate")
    List<AttendanceRecord> findByCourseIdAndDateRange(
        @Param("courseId") Long courseId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    @Query("SELECT ar FROM AttendanceRecord ar WHERE ar.session.sessionDate = :date")
    List<AttendanceRecord> findByDate(@Param("date") LocalDate date);
}
