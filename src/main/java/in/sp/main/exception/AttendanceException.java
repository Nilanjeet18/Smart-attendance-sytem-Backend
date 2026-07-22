package in.sp.main.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AttendanceException extends RuntimeException {

    private final String errorCode;

    public AttendanceException(String message) {
        super(message);
        this.errorCode = "ATTENDANCE_ERROR";
    }

    public AttendanceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public AttendanceException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "ATTENDANCE_ERROR";
    }

    public String getErrorCode() {
        return errorCode;
    }

    // ── Common factory methods ─────────────────────────────────────────────

    public static AttendanceException sessionNotActive(Long sessionId) {
        return new AttendanceException(
            "Session " + sessionId + " is not active", "SESSION_NOT_ACTIVE");
    }

    public static AttendanceException alreadyMarked(String rollNumber) {
        return new AttendanceException(
            "Attendance already marked for student: " + rollNumber, "ALREADY_MARKED");
    }

    public static AttendanceException qrTokenExpired() {
        return new AttendanceException(
            "QR code has expired. Please ask your teacher to refresh it.", "QR_EXPIRED");
    }

    public static AttendanceException qrTokenInvalid() {
        return new AttendanceException(
            "Invalid QR token.", "QR_INVALID");
    }

    public static AttendanceException faceNotRecognized(double score) {
        return new AttendanceException(
            String.format("Face not recognized (score: %.2f). Try again in better lighting.", score),
            "FACE_NOT_RECOGNIZED");
    }

    public static AttendanceException noFaceDetected() {
        return new AttendanceException(
            "No face detected in the image. Please try again.", "NO_FACE_DETECTED");
    }

    public static AttendanceException studentNotEnrolled(String rollNumber, String courseCode) {
        return new AttendanceException(
            "Student " + rollNumber + " is not enrolled in course " + courseCode,
            "STUDENT_NOT_ENROLLED");
    }
}
