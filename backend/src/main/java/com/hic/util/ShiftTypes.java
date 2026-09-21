package com.hic.util;

import java.util.Locale;
import java.util.Set;

/**
 * Recognizes and normalizes shift type codes used across timetable, employee, and UI filters.
 */
public final class ShiftTypes {

    public static final String FLEXIBLE = "FLEXIBLE";
    public static final String STANDARD = "STANDARD";
    public static final String NIGHT = "NIGHT";

    private static final Set<String> FLEXIBLE_ALIASES = Set.of(
            "FLEXIBLE", "FIRST_ENTRY", "SERBEST", "FREE_SHIFT", "FREE"
    );
    private static final Set<String> STANDARD_ALIASES = Set.of(
            "STANDARD", "STANDARD_SHIFT", "STANDART"
    );
    private static final Set<String> NIGHT_ALIASES = Set.of(
            "NIGHT", "LATE_SHIFT", "EXACT_SHIFT", "EXACT", "NIGHT_SHIFT"
    );

    private ShiftTypes() {
    }

    public static boolean isFlexible(String shiftType) {
        return FLEXIBLE.equals(canonical(shiftType));
    }

    /**
     * Maps UI filters and stored schedule types onto one of {@code FLEXIBLE}, {@code STANDARD},
     * {@code NIGHT}, or the uppercased original when unknown.
     */
    public static String canonical(String shiftType) {
        if (shiftType == null || shiftType.isBlank()) {
            return null;
        }
        String normalized = shiftType.trim().toUpperCase(Locale.ROOT);
        if (FLEXIBLE_ALIASES.contains(normalized) || normalized.contains("FLEXIBLE") || normalized.contains("SERBEST")) {
            return FLEXIBLE;
        }
        if (STANDARD_ALIASES.contains(normalized)) {
            return STANDARD;
        }
        if (NIGHT_ALIASES.contains(normalized) || normalized.contains("NIGHT")) {
            return NIGHT;
        }
        return normalized;
    }

    /**
     * True when the employee's assigned schedule type matches the report filter tab.
     * Blank filter means "All Shifts".
     */
    public static boolean matchesFilter(String assignedScheduleShiftType, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String assigned = canonical(assignedScheduleShiftType);
        if (assigned == null) {
            return false;
        }
        String wanted = canonical(filter);
        return wanted != null && wanted.equals(assigned);
    }
}
