package in.sp.main.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import in.sp.main.model.AttendanceSession;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    List<AttendanceSession> findByCourseId(Long courseId);

    List<AttendanceSession> findByCourseIdAndSessionDate(Long courseId, LocalDate date);

    List<AttendanceSession> findBySessionDate(LocalDate date);
    
    List<AttendanceSession> findByTeacherIdAndSessionDate(
    	    Long teacherId,
    	    LocalDate sessionDate
    	);

    Optional<AttendanceSession> findByCourseIdAndStatus(
        Long courseId, AttendanceSession.SessionStatus status);
    
    Optional<AttendanceSession> findByTeacherIdAndStatus(
    	    Long teacherId,
    	    AttendanceSession.SessionStatus status
    	);

    @Query("SELECT s FROM AttendanceSession s WHERE s.course.id = :courseId " +
           "AND s.sessionDate BETWEEN :startDate AND :endDate")
    List<AttendanceSession> findByCourseIdAndDateRange(
        @Param("courseId") Long courseId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(s) FROM AttendanceSession s WHERE s.course.id = :courseId " +
           "AND s.status = 'CLOSED'")
    long countCompletedSessionsByCourseId(@Param("courseId") Long courseId);

    List<AttendanceSession> findByStatus(AttendanceSession.SessionStatus status);
   
}