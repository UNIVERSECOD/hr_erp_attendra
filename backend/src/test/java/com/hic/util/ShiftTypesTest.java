package com.hic.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftTypesTest {

    @Test
    void recognizesFlexibleAliases() {
        assertThat(ShiftTypes.isFlexible("FLEXIBLE")).isTrue();
        assertThat(ShiftTypes.isFlexible("flexible")).isTrue();
        assertThat(ShiftTypes.isFlexible("FIRST_ENTRY")).isTrue();
        assertThat(ShiftTypes.isFlexible("SERBEST")).isTrue();
        assertThat(ShiftTypes.isFlexible("STANDARD")).isFalse();
        assertThat(ShiftTypes.isFlexible("NIGHT")).isFalse();
        assertThat(ShiftTypes.isFlexible(null)).isFalse();
        assertThat(ShiftTypes.isFlexible("")).isFalse();
    }

    @Test
    void matchesFilter_usesAssignedScheduleNotPunchHeuristics() {
        assertThat(ShiftTypes.matchesFilter("FLEXIBLE", "")).isTrue();
        assertThat(ShiftTypes.matchesFilter("FLEXIBLE", null)).isTrue();

        assertThat(ShiftTypes.matchesFilter("FLEXIBLE", "FLEXIBLE")).isTrue();
        assertThat(ShiftTypes.matchesFilter("FIRST_ENTRY", "FLEXIBLE")).isTrue();
        assertThat(ShiftTypes.matchesFilter("FLEXIBLE", "FIRST_ENTRY")).isTrue();

        assertThat(ShiftTypes.matchesFilter("STANDARD", "STANDARD")).isTrue();
        assertThat(ShiftTypes.matchesFilter("STANDARD", "STANDARD_SHIFT")).isTrue();
        assertThat(ShiftTypes.matchesFilter("STANDART", "STANDARD")).isTrue();

        assertThat(ShiftTypes.matchesFilter("NIGHT", "NIGHT")).isTrue();
        assertThat(ShiftTypes.matchesFilter("NIGHT", "LATE_SHIFT")).isTrue();
        assertThat(ShiftTypes.matchesFilter("LATE_SHIFT", "NIGHT")).isTrue();

        assertThat(ShiftTypes.matchesFilter("FLEXIBLE", "STANDARD")).isFalse();
        assertThat(ShiftTypes.matchesFilter("FLEXIBLE", "NIGHT")).isFalse();
        assertThat(ShiftTypes.matchesFilter("STANDARD", "FLEXIBLE")).isFalse();
        assertThat(ShiftTypes.matchesFilter(null, "FLEXIBLE")).isFalse();
    }

    @Test
    void canonical_normalizesUiAndScheduleCodes() {
        assertThat(ShiftTypes.canonical("FIRST_ENTRY")).isEqualTo(ShiftTypes.FLEXIBLE);
        assertThat(ShiftTypes.canonical("STANDARD_SHIFT")).isEqualTo(ShiftTypes.STANDARD);
        assertThat(ShiftTypes.canonical("LATE_SHIFT")).isEqualTo(ShiftTypes.NIGHT);
        assertThat(ShiftTypes.canonical("NIGHT")).isEqualTo(ShiftTypes.NIGHT);
    }
}
