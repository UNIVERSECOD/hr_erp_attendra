package com.hic.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AppTimeZoneTest {

    @Test
    void toLocalDateTime_preservesBakuWallClockFromOffset() {
        OffsetDateTime devicePunch = OffsetDateTime.of(2026, 7, 27, 22, 44, 0, 0, ZoneOffset.ofHours(4));
        LocalDateTime stored = AppTimeZone.toLocalDateTime(devicePunch);
        assertThat(stored).isEqualTo(LocalDateTime.of(2026, 7, 27, 22, 44));
    }

    @Test
    void toOffsetDateTime_attachesAsiaBakuOffset() {
        OffsetDateTime api = AppTimeZone.toOffsetDateTime(LocalDateTime.of(2026, 7, 27, 22, 44));
        assertThat(api.getHour()).isEqualTo(22);
        assertThat(api.getMinute()).isEqualTo(44);
        assertThat(api.getOffset().getTotalSeconds()).isEqualTo(4 * 3600);
    }
}
