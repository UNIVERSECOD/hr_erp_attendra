package com.abv.hrerpisapi.device.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HikvisionPayloadNormalizerTest {

    @Test
    void normalizeGender_mapsSupportedValues() {
        assertThat(HikvisionPayloadNormalizer.normalizeGender("MALE")).isEqualTo("male");
        assertThat(HikvisionPayloadNormalizer.normalizeGender(" female ")).isEqualTo("female");
    }

    @Test
    void normalizeGender_omitsUnknownAndUnsupportedValues() {
        assertThat(HikvisionPayloadNormalizer.normalizeGender("UNKNOWN")).isNull();
        assertThat(HikvisionPayloadNormalizer.normalizeGender("other")).isNull();
        assertThat(HikvisionPayloadNormalizer.normalizeGender(" ")).isNull();
        assertThat(HikvisionPayloadNormalizer.normalizeGender(null)).isNull();
    }
}
