package in.sp.main.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import in.sp.main.dto.AttendanceDTO;
import in.sp.main.dto.StudentDTO;
import in.sp.main.exception.AttendanceException;
import in.sp.main.service.FaceDetectionService;
import in.sp.main.service.StudentService;
import in.sp.main.util.FaceDetectionUtil;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/face")
@RequiredArgsConstructor
public class FaceDetectionController {

    private final FaceDetectionService faceDetectionService;
    private final FaceDetectionUtil    faceDetectionUtil;
    private final StudentService       studentService;

    // ── Helper: body madhun image kadhto ─────────────────────────────────

    /**
     * Body madhe "faceImageBase64" kiva "imageBase64" — donhi accept karto.
     * "data:image/..." prefix nahi asel tar aapobhap add karto.
     */
    private String extractImage(Map<String, String> body) {
        // Key 1: faceImageBase64
        String image = body.get("faceImageBase64");
        // Key 2: imageBase64 (fallback)
        if (image == null || image.isBlank()) {
            image = body.get("imageBase64");
        }
        // Key 3: image (fallback)
        if (image == null || image.isBlank()) {
            image = body.get("image");
        }
        if (image == null || image.isBlank()) {
            throw new AttendanceException(
                "Body madhe 'faceImageBase64' field required aahe. " +
                "Example: { \"faceImageBase64\": \"data:image/jpeg;base64,/9j/...\" }",
                "MISSING_IMAGE");
        }
        // "data:image/..." prefix nahi asel tar add karo
        if (!image.startsWith("data:")) {
            image = "data:image/jpeg;base64," + image;
        }
        return image;
    }

    // ── Face Registration ─────────────────────────────────────────────────

    /**
     * Student cha face register karo.
     * Body: { "faceImageBase64": "data:image/jpeg;base64,..." }
     *
     * POST /api/face/register/{studentId}
     */
    @PostMapping("/register/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<StudentDTO> registerFaceBase64(
            @PathVariable Long studentId,
            @RequestBody Map<String, String> body) {

        String imageBase64 = extractImage(body);
        log.info("Face registration for studentId={}", studentId);
        StudentDTO updated = studentService.registerFace(studentId, imageBase64);
        return ResponseEntity.ok(updated);
    }

    /**
     * File upload ne face register karo (multipart/form-data).
     * POST /api/face/register/{studentId}/upload
     */
    @PostMapping("/register/{studentId}/upload")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<StudentDTO> registerFaceUpload(
            @PathVariable Long studentId,
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new AttendanceException("File empty aahe", "EMPTY_FILE");
            }
            String mimeType = file.getContentType();
            if (mimeType == null || !mimeType.startsWith("image/")) {
                throw new AttendanceException("Fakt image files accept hotaat", "INVALID_FILE_TYPE");
            }
            byte[] bytes   = file.getBytes();
            String base64  = Base64.getEncoder().encodeToString(bytes);
            String dataUri = "data:" + mimeType + ";base64," + base64;

            log.info("Face registration via file upload for studentId={} ({}KB)",
                studentId, bytes.length / 1024);
            return ResponseEntity.ok(studentService.registerFace(studentId, dataUri));

        } catch (AttendanceException e) {
            throw e;
        } catch (Exception e) {
            throw new AttendanceException("File processing failed: " + e.getMessage(), "FILE_ERROR");
        }
    }

    // ── Face Detection (preview) ──────────────────────────────────────────

    /**
     * Image madhe face detect karo — attendance mark hot nahi.
     * POST /api/face/detect
     * Body: { "imageBase64": "data:image/jpeg;base64,..." }
     */
    @PostMapping("/detect")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> detectFaces(
            @RequestBody Map<String, String> body) {

        String imageBase64 = extractImage(body);

        List<FaceDetectionUtil.FaceRegion> faces =
            faceDetectionService.detectFacesOnly(imageBase64);

        String message;
        if (faces.isEmpty()) {
            message = "Koni face detect zala nahi — photo clear aahe ka? Prakash barobar aahe ka?";
        } else if (faces.size() == 1) {
            message = "Face detect zala! ✅";
        } else {
            message = faces.size() + " faces detect zale — fakt ek vyakti asayla hava";
        }

        return ResponseEntity.ok(Map.of(
            "faceCount",  faces.size(),
            "detected",   !faces.isEmpty(),
            "faces",      faces.stream().map(f -> Map.of(
                "x",      f.x(),
                "y",      f.y(),
                "width",  f.width(),
                "height", f.height()
            )).toList(),
            "message", message
        ));
    }

    // ── Face Attendance Scan ──────────────────────────────────────────────

    /**
     * Face scan ne attendance mark karo.
     * POST /api/face/scan
     * Body: { "sessionId": 1, "faceImageBase64": "data:image/jpeg;base64,..." }
     */
    @PostMapping("/scan")
    public ResponseEntity<AttendanceDTO> markAttendanceByFace(
            @RequestBody Map<String, Object> body) {

        Object sessionIdObj = body.get("sessionId");
        if (sessionIdObj == null) {
            throw new AttendanceException("sessionId required aahe", "MISSING_SESSION_ID");
        }

        Long sessionId;
        try {
            sessionId = Long.valueOf(sessionIdObj.toString());
        } catch (NumberFormatException e) {
            throw new AttendanceException("sessionId invalid aahe", "INVALID_SESSION_ID");
        }

        // Image kadhto — "faceImageBase64" kiva "imageBase64" donhi chaltat
        String imageBase64 = (String) body.get("faceImageBase64");
        if (imageBase64 == null || imageBase64.isBlank()) {
            imageBase64 = (String) body.get("imageBase64");
        }
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new AttendanceException(
                "faceImageBase64 field required aahe", "MISSING_IMAGE");
        }
        if (!imageBase64.startsWith("data:")) {
            imageBase64 = "data:image/jpeg;base64," + imageBase64;
        }

        log.info("Face attendance scan for sessionId={}", sessionId);
        AttendanceDTO result = faceDetectionService.markAttendanceByFace(sessionId, imageBase64);
        return ResponseEntity.ok(result);
    }

    // ── Face Compare ──────────────────────────────────────────────────────

    /**
     * Don photos compare karo.
     * POST /api/face/compare
     * Body: { "image1": "...", "image2": "..." }
     */
    @PostMapping("/compare")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> compareFaces(
            @RequestBody Map<String, String> body) {

        String image1 = body.get("image1");
        String image2 = body.get("image2");

        if (image1 == null || image2 == null) {
            throw new AttendanceException(
                "image1 aani image2 donhi required aahet", "MISSING_FIELDS");
        }
        if (!image1.startsWith("data:")) image1 = "data:image/jpeg;base64," + image1;
        if (!image2.startsWith("data:")) image2 = "data:image/jpeg;base64," + image2;

        String encoding1 = faceDetectionUtil.extractFaceEncoding(image1);
        String encoding2 = faceDetectionUtil.extractFaceEncoding(image2);
        double score     = faceDetectionUtil.compareFaceEncodings(encoding1, encoding2);
        boolean isSame   = faceDetectionUtil.isSamePerson(encoding1, encoding2);

        return ResponseEntity.ok(Map.of(
            "similarityScore", Math.round(score * 10000.0) / 10000.0,
            "isSamePerson",    isSame,
            "confidence",      (int)(score * 100) + "%",
            "verdict",         isSame ? "✅ Same person" : "❌ Different person"
        ));
    }

    // ── Status Check ──────────────────────────────────────────────────────

    /**
     * Student cha face registered aahe ka te check karo.
     * GET /api/face/status/{studentId}
     */
    @GetMapping("/status/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> checkFaceStatus(
            @PathVariable Long studentId) {
        StudentDTO student = studentService.getStudentById(studentId);
        boolean hasface = Boolean.TRUE.equals(student.getHasFaceEncoding());
        return ResponseEntity.ok(Map.of(
            "studentId",         studentId,
            "studentName",       student.getName(),
            "rollNumber",        student.getRollNumber(),
            "hasFaceRegistered", hasface,
            "message", hasface
                ? "✅ Face registered aahe — face attendance chalel!"
                : "❌ Face registered nahi — aadhi /api/face/register/{id} vaapara"
        ));
    }

    // ── Remove Face ───────────────────────────────────────────────────────

    /**
     * Student cha face encoding delete karo.
     * DELETE /api/face/register/{studentId}
     */
    @DeleteMapping("/register/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, String>> removeFaceRegistration(
            @PathVariable Long studentId) {
        var student = studentService.findById(studentId);
        student.setFaceEncoding(null);
        log.info("Face encoding removed for studentId={}", studentId);
        return ResponseEntity.ok(Map.of(
            "message",   "Face registration remove kela",
            "studentId", String.valueOf(studentId)
        ));
    }
}