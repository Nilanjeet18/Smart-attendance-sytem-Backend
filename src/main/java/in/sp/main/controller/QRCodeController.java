package in.sp.main.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import in.sp.main.dto.SessionDTO;
import in.sp.main.exception.ResourceNotFoundException;
import in.sp.main.model.QRToken;
import in.sp.main.repository.QRTokenRepository;
import in.sp.main.service.QRCodeService;
import in.sp.main.util.DateUtil;
import in.sp.main.util.QRCodeUtil;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/qr")
@RequiredArgsConstructor
public class QRCodeController {

    private final QRCodeService      qrCodeService;
    private final QRCodeUtil         qrCodeUtil;
    private final QRTokenRepository  qrTokenRepository;
    private final DateUtil           dateUtil;

    // ── Generate QR for a session ─────────────────────────────────────────

    /**
     * Generates (or regenerates) a QR code for the given session.
     * Returns JSON with token, base64 image, and expiry info.
     *
     * POST /api/qr/generate/{sessionId}
     */
    @PostMapping("/generate/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<SessionDTO> generateQR(@PathVariable Long sessionId) {
        log.info("QR generation requested for session id={}", sessionId);
        return ResponseEntity.ok(qrCodeService.generateQRForSession(sessionId));
    }

    /**
     * Refreshes (invalidates old + creates new) the QR code for a session.
     * Call this when the existing QR is about to expire or has been compromised.
     *
     * PUT /api/qr/refresh/{sessionId}
     */
    @PostMapping("/refresh/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<SessionDTO> refreshQR(@PathVariable Long sessionId) {
        log.info("QR refresh requested for session id={}", sessionId);
        return ResponseEntity.ok(qrCodeService.refreshQR(sessionId));
    }

    // ── Download QR as PNG image ──────────────────────────────────────────

    /**
     * Downloads the QR code as a PNG image file.
     * Teachers can print this or display on projector.
     *
     * GET /api/qr/download/{sessionId}
     */
    @GetMapping("/download/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<byte[]> downloadQRImage(@PathVariable Long sessionId) {
        QRToken token = qrTokenRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("QRToken", "sessionId", sessionId));

        if (token.getQrImageBase64() == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] imageBytes = Base64.getDecoder().decode(token.getQrImageBase64());
        String filename   = "attendance-qr-session-" + sessionId + ".png";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.IMAGE_PNG)
            .contentLength(imageBytes.length)
            .body(imageBytes);
    }

    // ── QR token status ───────────────────────────────────────────────────

    /**
     * Returns status info for the QR token currently associated with a session.
     * Used by teacher dashboard to show countdown timer.
     *
     * GET /api/qr/status/{sessionId}
     */
    @GetMapping("/status/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> getQRStatus(@PathVariable Long sessionId) {
        QRToken token = qrTokenRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("QRToken", "sessionId", sessionId));

        long secondsLeft = dateUtil.secondsUntilExpiry(token.getExpiresAt());
        boolean valid    = token.isValid();

        return ResponseEntity.ok(Map.of(
            "sessionId",        sessionId,
            "isValid",          valid,
            "isExpired",        token.isExpired(),
            "isUsed",           token.getIsUsed(),
            "scanCount",        token.getScanCount(),
            "generatedAt",      dateUtil.formatDateTime(token.getGeneratedAt()),
            "expiresAt",        dateUtil.formatDateTime(token.getExpiresAt()),
            "secondsRemaining", secondsLeft,
            "minutesRemaining", secondsLeft / 60
        ));
    }

    // ── Validate a token (used by students or external integrations) ───────

    /**
     * Validates whether a QR token string is currently active and not expired.
     * Returns token metadata without marking attendance.
     *
     * GET /api/qr/validate?token={token}
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestParam String token) {
        QRToken qrToken = qrTokenRepository.findByToken(token).orElse(null);

        if (qrToken == null) {
            return ResponseEntity.ok(Map.of(
                "valid",   false,
                "reason",  "Token not found"
            ));
        }

        boolean isValid = qrToken.isValid();
        long secondsLeft = dateUtil.secondsUntilExpiry(qrToken.getExpiresAt());

        Map<String, Object> response = Map.of(
            "valid",            isValid,
            "expired",          qrToken.isExpired(),
            "used",             qrToken.getIsUsed(),
            "sessionId",        qrToken.getSession().getId(),
            "courseCode",       qrToken.getSession().getCourse().getCourseCode(),
            "secondsRemaining", secondsLeft,
            "reason",           !isValid
                ? (qrToken.isExpired() ? "Token has expired" : "Token already invalidated")
                : "Token is valid"
        );
        return ResponseEntity.ok(response);
    }

    // ── Generate arbitrary QR (utility) ──────────────────────────────────

    /**
     * Generates a one-off QR code from any text (not linked to a session).
     * Useful for testing or custom integrations.
     *
     * POST /api/qr/encode
     * Body: { "content": "https://example.com" }
     */
    @PostMapping("/encode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> encodeText(
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String base64 = qrCodeUtil.generateQRCodeBase64(content);
        return ResponseEntity.ok(Map.of(
            "content",      content,
            "base64Image",  base64,
            "dataUri",      "data:image/png;base64," + base64
        ));
    }

    // ── Decode QR image ───────────────────────────────────────────────────

    /**
     * Decodes a QR code image (base64) and returns the embedded text.
     * Useful for debugging or verification from the admin panel.
     *
     * POST /api/qr/decode
     * Body: { "imageBase64": "<base64 string>" }
     */
    @PostMapping("/decode")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, String>> decodeQR(
            @RequestBody Map<String, String> body) {
        String imageBase64 = body.get("imageBase64");
        if (imageBase64 == null || imageBase64.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String decoded = qrCodeUtil.decodeQRCode(imageBase64);
        return ResponseEntity.ok(Map.of("decodedText", decoded));
    }
}
