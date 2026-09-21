package com.abv.hrerpisapi.model.request.device;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the JSON body for ISAPI AcsEvent/Search requests.
 */
public class AcsEventSearchRequest {

    private static final ObjectMapper OM = new ObjectMapper();

    private AcsEventSearchRequest() {
    }

    /**
     * @param startTime ISO-8601 without offset, e.g. "2024-01-01T00:00:00"
     * @param endTime   ISO-8601 without offset
     * @param maxResults max number of results to return
     */
    public static String forMajor5(String startTime, String endTime, int maxResults) {
        return """
                {"AcsEventCond":{"searchID":"1","searchResultPosition":0,"maxResults":%d,\
                "major":5,"startTime":"%s","endTime":"%s"}}"""
                .formatted(maxResults, startTime, endTime);
    }

    public static String fromCondition(AcsEventCondRequest cond) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("searchID", isBlank(cond.searchID()) ? UUID.randomUUID().toString() : cond.searchID());
        normalized.put("searchResultPosition", cond.searchResultPosition() == null ? 0 : cond.searchResultPosition());
        normalized.put("maxResults", cond.maxResults() == null ? 50 : cond.maxResults());
        if (cond.major() != null) {
            normalized.put("major", cond.major());
        }
        if (cond.minor() != null) {
            normalized.put("minor", cond.minor());
        }
        if (!isBlank(cond.employeeNoString())) {
            normalized.put("employeeNoString", cond.employeeNoString());
        }
        if (!isBlank(cond.cardNo())) {
            normalized.put("cardNo", cond.cardNo());
        }
        normalized.put("startTime", cond.startTime());
        normalized.put("endTime", cond.endTime());
        if (cond.picEnable() != null) {
            normalized.put("picEnable", cond.picEnable());
        }

        try {
            return OM.writeValueAsString(Map.of("AcsEventCond", normalized));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize AcsEventCond", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record AcsEventCondRequest(
            String searchID,
            Integer searchResultPosition,
            Integer maxResults,
            Integer major,
            Integer minor,
            String startTime,
            String endTime,
            Boolean picEnable,
            String employeeNoString,
            String cardNo
    ) {
    }
}
