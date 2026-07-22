package in.sp.main.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.sp.main.dto.SessionDTO;
import in.sp.main.exception.AttendanceException;
import in.sp.main.exception.ResourceNotFoundException;
import in.sp.main.model.AttendanceSession;
import in.sp.main.model.QRToken;
import in.sp.main.repository.AttendanceSessionRepository;
import in.sp.main.repository.QRTokenRepository;
import in.sp.main.util.QRCodeUtil;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QRCodeService {

    private final QRTokenRepository           qrTokenRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final QRCodeUtil                  qrCodeUtil;
    private final EntityManager               entityManager;  // flush sathi

    @Value("${attendance.qr.expiry-minutes:10}")
    private int qrExpiryMinutes;

    @Value("${attendance.qr.base-url:http://localhost:3000}")
    private String baseUrl;

    // ── Generate QR for a session ─────────────────────────────────────────

    @Transactional
    public SessionDTO generateQRForSession(Long sessionId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Session", "id", sessionId));

        if (session.getStatus() != AttendanceSession.SessionStatus.ACTIVE) {
            throw AttendanceException.sessionNotActive(sessionId);
        }

        // Step 1: Juna token delete karo
        qrTokenRepository.deleteBySessionId(sessionId);

        // Step 2: DELETE flush karo — naahi tar same transaction madhe
        //         INSERT la "Duplicate entry" error yet hota
        entityManager.flush();

        // Step 3: Nava token banav
        String rawToken = UUID.randomUUID().toString() + "-" + sessionId + "-"
            + System.currentTimeMillis();
        String encodedToken = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(rawToken.getBytes());

        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(qrExpiryMinutes);

        // Step 4: Scan URL — frontend QRScanPage la point karto (localhost:3000)
        //         Student cha phone browser madhe hi URL open hoil
        String scanUrl = baseUrl + "/qr-scan?token=" + encodedToken;
        String qrImageBase64 = qrCodeUtil.generateQRCodeBase64(scanUrl);

        QRToken qrToken = QRToken.builder()
            .session(session)
            .token(encodedToken)
            .qrImageBase64(qrImageBase64)
            .generatedAt(now)
            .expiresAt(expiresAt)
            .isUsed(false)
            .scanCount(0)
            .build();

        QRToken saved = qrTokenRepository.save(qrToken);
        log.info("Generated QR token for session {} (expires: {})", sessionId, expiresAt);

        long secondsLeft = ChronoUnit.SECONDS.between(now, expiresAt);
        return SessionDTO.builder()
            .id(sessionId)
            .qrToken(saved.getToken())
            .qrImageBase64(saved.getQrImageBase64())
            .qrExpiresInSeconds(secondsLeft)
            .build();
    }

    // ── Refresh QR ────────────────────────────────────────────────────────

    @Transactional
    public SessionDTO refreshQR(Long sessionId) {
        log.info("Refreshing QR token for session {}", sessionId);
        return generateQRForSession(sessionId);
    }

    // ── Validate a scanned token ──────────────────────────────────────────

    @Transactional
    public QRToken validateAndIncrementScan(String token, String clientIp) {

        QRToken qrToken = qrTokenRepository.findByToken(token)
            .orElseThrow(AttendanceException::qrTokenInvalid);

        if (qrToken.isExpired()) {
            throw AttendanceException.qrTokenExpired();
        }

        if (qrToken.getIsUsed()) {
            throw new AttendanceException(
                "This QR code has been invalidated. Ask teacher to refresh.", "QR_INVALIDATED");
        }

        if (qrToken.getMaxScans() != null && qrToken.getScanCount() >= qrToken.getMaxScans()) {
            throw new AttendanceException("QR code scan limit reached", "QR_SCAN_LIMIT");
        }

        qrToken.setScanCount(qrToken.getScanCount() + 1);
        qrTokenRepository.save(qrToken);

        log.debug("QR token validated. Session={}, totalScans={}",
            qrToken.getSession().getId(), qrToken.getScanCount());
        return qrToken;
    }

    // ── Scheduled cleanup ─────────────────────────────────────────────────

    @Scheduled(fixedRateString = "300000")
    @Transactional
    public void cleanupExpiredTokens() {
        int count = qrTokenRepository.markExpiredTokensAsUsed(LocalDateTime.now());
        if (count > 0) {
            log.info("Marked {} expired QR tokens as used", count);
        }
    }
}
