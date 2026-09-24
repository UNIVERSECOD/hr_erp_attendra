package com.hic.service;

import com.hic.model.AttendanceLog;
import com.hic.util.ShiftTypes;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Work-date attendance inference.
 * <p>
 * A session belongs to the local date on which it started. Sessions that cross midnight
 * remain one session and their full duration is credited to that work date.
 * <p>
 * Worked minutes depend on shift type:
 * <ul>
 *   <li>Standard / Night — span from first check-in to last check-out</li>
 *   <li>Flexible — sum of each closed check-in/check-out interval</li>
 * </ul>
 */
@Service
public class AttendanceInferenceService {

    private static final Duration DUPLICATE_PUNCH_WINDOW = Duration.ofSeconds(60);

    public AttendanceInference inferDay(List<AttendanceLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return new AttendanceInference(null, null, 0, false, List.of());
        }
        LocalDate day = logs.stream()
                .map(AttendanceLog::getCheckInTime)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
        return inferDay(logs, day);
    }

    public AttendanceInference inferDay(List<AttendanceLog> logs, LocalDate day) {
        if (day == null) {
            return inferDay(logs);
        }
        if (logs == null || logs.isEmpty()) {
            return new AttendanceInference(null, null, 0, false, List.of());
        }

        List<SessionSegment> segments = new ArrayList<>();
        LocalDateTime firstEntry = null;
        LocalDateTime lastExit = null;
        int workedMinutes = 0;
        boolean currentlyInside = false;

        List<AttendanceLog> ordered = dedupeSessions(logs);

        for (AttendanceLog log : ordered) {
            LocalDateTime entry = log.getCheckInTime();
            if (entry == null || !entry.toLocalDate().equals(day)) {
                continue;
            }
            LocalDateTime exit = log.getCheckOutTime();

            if (exit == null) {
                if (firstEntry == null || entry.isBefore(firstEntry)) {
                    firstEntry = entry;
                }
                currentlyInside = true;
                segments.add(new SessionSegment(entry, null));
                continue;
            }

            if (!exit.isAfter(entry)) {
                continue;
            }

            workedMinutes += safeMinutes(Duration.between(entry, exit));
            if (firstEntry == null || entry.isBefore(firstEntry)) {
                firstEntry = entry;
            }
            if (lastExit == null || exit.isAfter(lastExit)) {
                lastExit = exit;
            }
            segments.add(new SessionSegment(entry, exit));
        }

        return new AttendanceInference(firstEntry, lastExit, workedMinutes, currentlyInside, List.copyOf(segments));
    }

    /**
     * Returns true when the session belongs to {@code workDate}.
     */
    public boolean belongsToWorkDate(AttendanceLog log, LocalDate workDate) {
        return log != null
                && workDate != null
                && log.getCheckInTime() != null
                && log.getCheckInTime().toLocalDate().equals(workDate);
    }

    /** Backward-compatible name for callers while preserving work-date semantics. */
    public boolean overlapsDay(AttendanceLog log, LocalDate day) {
        return belongsToWorkDate(log, day);
    }

    /**
     * Collapses near-identical sessions (same check-in / check-out within
     * {@link #DUPLICATE_PUNCH_WINDOW}) that can appear after concurrent device syncs.
     */
    public List<AttendanceLog> dedupeSessions(List<AttendanceLog> logs) {
        List<AttendanceLog> ordered = logs.stream()
                .filter(log -> log.getCheckInTime() != null)
                .sorted(Comparator.comparing(AttendanceLog::getCheckInTime)
                        .thenComparing(log -> log.getCheckOutTime() != null ? log.getCheckOutTime() : LocalDateTime.MAX))
                .toList();

        List<AttendanceLog> deduped = new ArrayList<>();
        for (AttendanceLog log : ordered) {
            if (deduped.isEmpty()) {
                deduped.add(log);
                continue;
            }
            AttendanceLog previous = deduped.get(deduped.size() - 1);
            boolean sameStart = Duration.between(previous.getCheckInTime(), log.getCheckInTime()).abs()
                    .compareTo(DUPLICATE_PUNCH_WINDOW) <= 0;
            boolean sameEnd = previous.getCheckOutTime() != null && log.getCheckOutTime() != null
                    && Duration.between(previous.getCheckOutTime(), log.getCheckOutTime()).abs()
                    .compareTo(DUPLICATE_PUNCH_WINDOW) <= 0;
            if (sameStart && (sameEnd || previous.getCheckOutTime() == null || log.getCheckOutTime() == null)) {
                // Prefer the longer / more complete session
                if (previous.getCheckOutTime() == null && log.getCheckOutTime() != null) {
                    deduped.set(deduped.size() - 1, log);
                }
                continue;
            }
            deduped.add(log);
        }
        return deduped;
    }

    public record SessionSegment(LocalDateTime checkInTime, LocalDateTime checkOutTime) {
    }

    public record AttendanceInference(
            LocalDateTime firstEntry,
            LocalDateTime lastExit,
            int workedMinutes,
            boolean currentlyInside,
            List<SessionSegment> segments
    ) {
        public AttendanceInference(LocalDateTime firstEntry, LocalDateTime lastExit, int workedMinutes, boolean currentlyInside) {
            this(firstEntry, lastExit, workedMinutes, currentlyInside, List.of());
        }

        /** Sum of closed check-in/check-out intervals (flexible-shift basis). */
        public int intervalWorkedMinutes() {
            return workedMinutes;
        }

        /** First check-in → last check-out span (standard/night basis). */
        public int spanWorkedMinutes() {
            if (firstEntry == null || lastExit == null || !lastExit.isAfter(firstEntry)) {
                return 0;
            }
            return (int) Duration.between(firstEntry, lastExit).toMinutes();
        }

        /**
         * Flexible shifts sum each interval; standard/night use first-in to last-out span.
         * When any session is still open, only closed intervals are countable; using the
         * full span would invent worked time for the missing exit.
         */
        public int workedMinutesForShift(String shiftType) {
            return ShiftTypes.isFlexible(shiftType) || currentlyInside
                    ? intervalWorkedMinutes()
                    : spanWorkedMinutes();
        }

        public double workedHours() {
            return workedMinutes / 60.0;
        }

        public double workedHoursForShift(String shiftType) {
            return workedMinutesForShift(shiftType) / 60.0;
        }
    }

    private int safeMinutes(Duration duration) {
        long minutes = Math.max(duration.toMinutes(), 0);
        return minutes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) minutes;
    }
}
