package in.sp.main.util;

import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for all date/time operations used across the Smart Attendance System.
 *
 * Covers:
 *  - Formatting and parsing
 *  - Attendance period helpers (current week, month, semester)
 *  - Duration / difference calculations
 *  - Academic calendar helpers
 */
@Component
public class DateUtil {

    // ── Common formatters ──────────────────────────────────────────────────

    public static final DateTimeFormatter DATE_DISPLAY      = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    public static final DateTimeFormatter DATE_ISO          = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter DATETIME_DISPLAY  = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    public static final DateTimeFormatter DATETIME_COMPACT  = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    public static final DateTimeFormatter TIME_DISPLAY      = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter REPORT_HEADER     = DateTimeFormatter.ofPattern("MMMM dd, yyyy");

    // ── Current date/time ──────────────────────────────────────────────────

    public LocalDate today() {
        return LocalDate.now();
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    // ── Formatting ─────────────────────────────────────────────────────────

    public String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_DISPLAY) : "";
    }

    public String formatDateISO(LocalDate date) {
        return date != null ? date.format(DATE_ISO) : "";
    }

    public String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_DISPLAY) : "";
    }

    public String formatDateTimeCompact(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_COMPACT) : "";
    }

    public String formatTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(TIME_DISPLAY) : "";
    }

    public String formatForReportHeader(LocalDate date) {
        return date != null ? date.format(REPORT_HEADER) : "";
    }

    // ── Parsing ────────────────────────────────────────────────────────────

    public LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            // Try ISO first (yyyy-MM-dd), then display format (dd-MM-yyyy)
            try {
                return LocalDate.parse(dateStr, DATE_ISO);
            } catch (DateTimeParseException e) {
                return LocalDate.parse(dateStr, DATE_DISPLAY);
            }
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Cannot parse date: '" + dateStr
                + "'. Expected format: yyyy-MM-dd or dd-MM-yyyy");
        }
    }

    public LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) return null;
        try {
            return LocalDateTime.parse(dateTimeStr, DATETIME_DISPLAY);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Cannot parse datetime: '" + dateTimeStr + "'");
        }
    }

    // ── Period helpers ─────────────────────────────────────────────────────

    /** Returns [Monday, Sunday] of the current week. */
    public LocalDate[] currentWeekRange() {
        LocalDate today = today();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return new LocalDate[]{monday, sunday};
    }

    /** Returns [1st of month, last day of month] for the current month. */
    public LocalDate[] currentMonthRange() {
        LocalDate today = today();
        LocalDate first = today.withDayOfMonth(1);
        LocalDate last  = today.with(TemporalAdjusters.lastDayOfMonth());
        return new LocalDate[]{first, last};
    }

    /**
     * Returns the approximate semester date range based on current month.
     * Odd  semester: July  – November
     * Even semester: January – May
     */
    public LocalDate[] currentSemesterRange() {
        LocalDate today = today();
        int month = today.getMonthValue();
        int year  = today.getYear();

        LocalDate start, end;
        if (month >= 7 && month <= 11) {
            // Odd semester
            start = LocalDate.of(year, Month.JULY, 1);
            end   = LocalDate.of(year, Month.NOVEMBER, 30);
        } else if (month >= 1 && month <= 5) {
            // Even semester
            start = LocalDate.of(year, Month.JANUARY, 1);
            end   = LocalDate.of(year, Month.MAY, 31);
        } else {
            // June or December — vacation; return ±30 days around today
            start = today.minusDays(30);
            end   = today.plusDays(30);
        }
        return new LocalDate[]{start, end};
    }

    /** Returns all dates between start and end (inclusive). */
    public List<LocalDate> getDatesBetween(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            dates.add(current);
            current = current.plusDays(1);
        }
        return dates;
    }

    /** Returns all working days (Mon–Sat) between start and end (inclusive). */
    public List<LocalDate> getWorkingDaysBetween(LocalDate start, LocalDate end) {
        List<LocalDate> workingDays = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                workingDays.add(current);
            }
            current = current.plusDays(1);
        }
        return workingDays;
    }

    // ── Duration / difference calculations ────────────────────────────────

    /** Minutes between two LocalDateTimes (absolute value). */
    public long minutesBetween(LocalDateTime from, LocalDateTime to) {
        return Math.abs(ChronoUnit.MINUTES.between(from, to));
    }

    /** Days between two LocalDates (absolute value). */
    public long daysBetween(LocalDate from, LocalDate to) {
        return Math.abs(ChronoUnit.DAYS.between(from, to));
    }

    /** Seconds remaining until a future expiry time. Returns 0 if already expired. */
    public long secondsUntilExpiry(LocalDateTime expiresAt) {
        long seconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), expiresAt);
        return Math.max(0, seconds);
    }

    /** Returns true if the given dateTime is within the past N minutes. */
    public boolean isWithinLastMinutes(LocalDateTime dateTime, int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        return dateTime.isAfter(threshold);
    }

    /** Returns true if the current time is past the given deadline. */
    public boolean isPast(LocalDateTime deadline) {
        return LocalDateTime.now().isAfter(deadline);
    }

    /** Returns true if the given date is today. */
    public boolean isToday(LocalDate date) {
        return date.equals(today());
    }

    // ── Attendance-specific helpers ────────────────────────────────────────

    /**
     * Determines if a student is LATE based on session start time and arrival time.
     *
     * @param sessionStart   when the session officially began
     * @param arrivalTime    when the student marked attendance
     * @param graceMinutes   grace period in minutes before marking LATE
     * @return true if the student is late
     */
    public boolean isLate(LocalDateTime sessionStart, LocalDateTime arrivalTime, int graceMinutes) {
        return ChronoUnit.MINUTES.between(sessionStart, arrivalTime) > graceMinutes;
    }

    /**
     * Calculates attendance percentage.
     *
     * @param classesAttended number of classes attended
     * @param totalClasses    total classes held
     * @return percentage rounded to 2 decimal places
     */
    public double calculateAttendancePercentage(int classesAttended, int totalClasses) {
        if (totalClasses == 0) return 0.0;
        double raw = (classesAttended * 100.0) / totalClasses;
        return Math.round(raw * 100.0) / 100.0;
    }

    /**
     * Returns the minimum classes a student must attend to reach the required percentage.
     *
     * @param currentAttended  classes attended so far
     * @param totalSoFar       total classes held so far
     * @param remainingClasses classes yet to be held
     * @param requiredPct      required attendance percentage (e.g. 75.0)
     * @return minimum additional classes student must attend
     */
    public int minClassesToReachTarget(int currentAttended, int totalSoFar,
                                       int remainingClasses, double requiredPct) {
        int totalFinal = totalSoFar + remainingClasses;
        double required = (requiredPct / 100.0) * totalFinal;
        int minAdditional = (int) Math.ceil(required - currentAttended);
        return Math.max(0, Math.min(minAdditional, remainingClasses));
    }

    /**
     * Returns how many more classes a student can miss while staying above the threshold.
     *
     * @param currentAttended  classes attended so far
     * @param totalSoFar       total classes held so far
     * @param requiredPct      required attendance percentage
     * @return number of classes that can still be missed (0 if already at risk)
     */
    public int classesCanStillMiss(int currentAttended, int totalSoFar, double requiredPct) {
        if (totalSoFar == 0) return 0;
        // x = max total where (currentAttended / x) >= requiredPct/100
        // x <= currentAttended * 100 / requiredPct
        int maxTotal = (int) Math.floor((currentAttended * 100.0) / requiredPct);
        int canMiss  = maxTotal - totalSoFar;
        return Math.max(0, canMiss);
    }

    // ── Report filename helpers ────────────────────────────────────────────

    public String generateReportFilename(String prefix, String courseCode, LocalDate date) {
        return prefix + "_" + courseCode + "_" + date.format(DATETIME_COMPACT.ofPattern("yyyyMMdd")) + "_"
            + LocalDateTime.now().format(DATETIME_COMPACT);
    }

    public String todayForFilename() {
        return today().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}