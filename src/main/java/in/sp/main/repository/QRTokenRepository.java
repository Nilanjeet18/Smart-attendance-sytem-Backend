package in.sp.main.repository;

import in.sp.main.model.QRToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QRTokenRepository extends JpaRepository<QRToken, Long> {

    Optional<QRToken> findByToken(String token);

    Optional<QRToken> findBySessionId(Long sessionId);

    // ✅ Fix: The old token must be deleted in generateQRForSession before generating a new one.
    // Otherwise, a duplicate entry error occurs because 
    // the session_id column has a UNIQUE constraint.
    
    @Modifying
    @Transactional
    @Query("DELETE FROM QRToken qt WHERE qt.session.id = :sessionId")
    void deleteBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT qt FROM QRToken qt WHERE qt.token = :token " +
           "AND qt.expiresAt > :now AND qt.isUsed = false")
    Optional<QRToken> findValidToken(
        @Param("token") String token,
        @Param("now") LocalDateTime now);

    @Query("SELECT qt FROM QRToken qt WHERE qt.expiresAt < :now AND qt.isUsed = false")
    List<QRToken> findExpiredTokens(@Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE QRToken qt SET qt.isUsed = true WHERE qt.expiresAt < :now")
    int markExpiredTokensAsUsed(@Param("now") LocalDateTime now);
}
