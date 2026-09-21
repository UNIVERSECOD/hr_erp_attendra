package com.hic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTOs for Hikvision ISAPI AccessControl UserInfo Search endpoint.
 *
 * Request  → POST /ISAPI/AccessControl/UserInfo/Search?format=json
 * Response ← JSON containing UserInfoSearch wrapper with UserInfo list.
 */
public class HikUserInfoSearchDTO {

    // -------------------------------------------------------------------------
    // REQUEST
    // -------------------------------------------------------------------------

    /**
     * Top-level request wrapper sent to the device.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {

        @JsonProperty("UserInfoSearchCond")
        private SearchCond userInfoSearchCond;
    }

    /**
     * Pagination / search condition block inside the request.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchCond {

        /** Arbitrary string; the device echoes it back so parallel calls can be correlated. */
        @JsonProperty("searchID")
        private String searchId;

        /** Maximum number of records the device should return in one response. */
        @JsonProperty("maxResults")
        private int maxResults;

        /** Zero-based offset: how many records to skip (increases by maxResults each page). */
        @JsonProperty("searchResultPosition")
        private int searchResultPosition;
    }

    // -------------------------------------------------------------------------
    // RESPONSE
    // -------------------------------------------------------------------------

    /**
     * Top-level response wrapper returned by the device.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResponse {

        @JsonProperty("UserInfoSearch")
        private SearchResult userInfoSearch;
    }

    /**
     * The main body of the search response.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResult {

        /** Echoed back from the request. */
        @JsonProperty("searchID")
        private String searchId;

        /**
         * Status of the search:
         * "OK"           – results found.
         * "NO MATCHES"   – no results (empty device or end of pagination).
         */
        @JsonProperty("responseStatusStrg")
        private String responseStatusStrg;

        /** Total number of matched records on the device (may be 0 when status is "NO MATCHES"). */
        @JsonProperty("numOfMatches")
        private int numOfMatches;

        /** The actual user records returned in this page. May be null when no results. */
        @JsonProperty("UserInfo")
        private List<UserInfo> userInfoList;
    }

    /**
     * A single user record as returned by the device.
     * Fields are mapped from the Hikvision ISAPI JSON schema.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserInfo {

        /** Employee / card number – used as the unique identifier on the device. */
        @JsonProperty("employeeNo")
        private String employeeNo;

        /** Full display name stored on the device. */
        @JsonProperty("name")
        private String name;

        /** User type: "normal", "visitor", "blackList", etc. */
        @JsonProperty("userType")
        private String userType;

        /** Gender: "male" / "female" (device values, lowercase). */
        @JsonProperty("gender")
        private String gender;

        /** Whether a local PIN is enabled on the device for this user. */
        @JsonProperty("localUIRight")
        private Boolean localUIRight;

        /** Number of authentication failures allowed. */
        @JsonProperty("numOfFace")
        private Integer numOfFace;

        /** Validity period nested object. */
        @JsonProperty("Valid")
        private ValidityInfo valid;
    }

    /**
     * Validity period for a user record on the device.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValidityInfo {

        @JsonProperty("enable")
        private Boolean enable;

        /** ISO-8601 local date-time string, e.g. "2024-01-01T00:00:00". */
        @JsonProperty("beginTime")
        private String beginTime;

        /** ISO-8601 local date-time string, e.g. "2034-12-31T23:59:59". */
        @JsonProperty("endTime")
        private String endTime;

        /** Time zone type: "local" or "UTC". */
        @JsonProperty("timeType")
        private String timeType;
    }

    // -------------------------------------------------------------------------
    // RESULT SUMMARY (returned to the API caller)
    // -------------------------------------------------------------------------

    /**
     * Summary returned to the REST caller after the import operation completes.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportResultDTO {

        /** Total user records fetched from the device across all pages. */
        private int totalFetched;

        /** Records newly created in the local DB (did not exist before). */
        private int created;

        /** Records that were already present (skipped / not overwritten). */
        private int skipped;

        /** Records that caused an error and were not saved. */
        private int errors;

        /** Human-readable summary message. */
        private String message;
    }
}
