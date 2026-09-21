package com.abv.hrerpisapi.device.client;

public final class HikvisionPayloadNormalizer {

    private HikvisionPayloadNormalizer() {
    }

    public static String normalizeGender(String gender) {
        String normalized = normalizeOptionalText(gender);
        if (normalized == null) {
            return null;
        }
        if ("MALE".equalsIgnoreCase(normalized)) {
            return "male";
        }
        if ("FEMALE".equalsIgnoreCase(normalized)) {
            return "female";
        }
        // Some terminals reject "UNKNOWN" and other vendor-specific values.
        // Omitting the optional field is accepted across the supported models.
        return null;
    }

    public static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
