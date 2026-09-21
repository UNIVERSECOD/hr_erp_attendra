package com.hic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTOs for the Device Log Search feature.
 *
 * <p>The outer {@link SearchRequest} / {@link AcsEventCond} are serialised and
 * POSTed to the Hikvision device's {@code /ISAPI/AccessControl/AcsEvent?format=json}
 * endpoint.  The device reply is deserialised into {@link DeviceResponse} /
 * {@link AcsEvent} / {@link EventInfo}.  The backend then maps qualifying
 * entries to {@link EventEntryDTO} and returns a {@link SearchResultDTO} to
 * the React frontend.
 *
 * <p><b>Note on auth:</b> Hikvision devices require standard HTTP Digest
 * Authentication for ISAPI calls.  This is handled by {@code DeviceLogSearchService}
 * using Apache HttpClient 4.x's built-in {@code CredentialsProvider} / Digest scheme.
 */
public class DeviceLogSearchDTO {

    // ─────────────────────────────────────────────────────────────────────────
    // Request to device
    // ─────────────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        @JsonProperty("AcsEventCond")
        private AcsEventCond acsEventCond;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AcsEventCond {
        private String  searchID;
        private int     searchResultPosition;
        private int     maxResults;
        /** major=5 → Access Control category */
        private int     major;
        /** minor=75 → Card/Face verification success (confirmed via real device test) */
        private int     minor;
        private String  startTime;
        private String  endTime;
        private String  employeeNoString;
        private String  cardNo;
        private boolean picEnable;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Device response
    // ─────────────────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeviceResponse {
        @JsonProperty("AcsEvent")
        private AcsEvent acsEvent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AcsEvent {
        private String          searchID;
        /**
         * "OK" = last page; "MORE" = more results exist beyond current page.
         * Both indicate a successful response.
         */
        private String          responseStatusStrg;
        private int             numOfMatches;
        private int             totalMatches;
        @JsonProperty("InfoList")
        private List<EventInfo> infoList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventInfo {
        private int     major;
        private int     minor;
        private String  time;
        private String  name;
        private String  employeeNoString;
        private String  cardNo;
        /** Number of associated pictures; > 0 means a photo is available. */
        private Integer picturesNumber;
        /**
         * Full picture URL including trailing device token, e.g.
         * {@code http://192.168.1.101/LOCALS/pic/.../05_004031_30075_0.jpeg@WEB000000001062}.
         * The {@code @WEB…} suffix must be preserved exactly.
         */
        private String  pictureURL;
        private String  currentVerifyMode;
        private Integer cardType;
        private Integer doorNo;
        private String  userType;
        private Integer serialNo;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Frontend-facing DTOs
    // ─────────────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventEntryDTO {
        private String  employeeId;
        private String  name;
        private String  cardNo;
        private String  eventDescription;
        private String  time;
        private boolean hasPicture;
        /** Only populated when hasPicture=true; used by the photo-proxy endpoint. */
        private String  pictureURL;
        private String  verifyMode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResultDTO {
        private List<EventEntryDTO> items;
        private int                 totalMatches;
        private int                 numOfMatches;
        private int                 page;
        private int                 pageSize;
        /** "OK", "MORE", or "NO MATCHES" as returned by the device. */
        private String              responseStatus;
    }
}
