package com.hic.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Single application timezone for attendance wall-clock times.
 * Hikvision devices in Azerbaijan report local time (+04:00); we store and display
 * that same wall clock everywhere — never mix UTC and local.
 */
public final class AppTimeZone {

    public static final ZoneId ZONE = ZoneId.of("Asia/Baku");

    private AppTimeZone() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    /** Convert a device/API instant to Asia/Baku wall-clock LocalDateTime for DB storage. */
    public static LocalDateTime toLocalDateTime(OffsetDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZoneSameInstant(ZONE).toLocalDateTime();
    }

    /** Attach Asia/Baku offset to a stored wall-clock LocalDateTime for API responses. */
    public static OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(ZONE).toOffsetDateTime();
    }

    public static OffsetDateTime nowOffset() {
        return ZonedDateTime.now(ZONE).toOffsetDateTime();
    }
}
