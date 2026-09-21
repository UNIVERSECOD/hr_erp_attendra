package com.hic.service;

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
        return normalized;
    }

    public static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
