package com.abv.hrerpisapi.model.response.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Relevant fields from ISAPI AcsEvent/Search response.
 */
public record AcsEventSearchResponse(
        String searchID,
        String responseStatusStrg,
        int numOfMatches,
        int totalMatches,
        List<AcsEventItem> events
) {
    private static final ObjectMapper OM = new ObjectMapper();

    public static AcsEventSearchResponse fromJson(String body) {
        try {
            JsonNode root = OM.readTree(body).path("AcsEvent");
            JsonNode infoList = root.path("InfoList");
            List<AcsEventItem> events = new ArrayList<>();
            if (infoList.isArray()) {
                for (JsonNode event : infoList) {
                    events.add(new AcsEventItem(
                            event.path("serialNo").asLong(-1),
                            event.path("major").asInt(event.path("majorEventType").asInt(-1)),
                            event.path("minor").asInt(event.path("subEventType").asInt(-1)),
                            event.path("time").asText(null),
                            event.path("name").asText(null),
                            event.path("employeeNoString").asText(""),
                            event.path("cardNo").asText(""),
                            event.path("pictureURL").asText(null),
                            event.path("currentVerifyMode").asText(null),
                            event.path("picturesNumber").asInt(0)
                    ));
                }
            }

            return new AcsEventSearchResponse(
                    root.path("searchID").asText(""),
                    root.path("responseStatusStrg").asText(""),
                    root.path("numOfMatches").asInt(0),
                    root.path("totalMatches").asInt(0),
                    events
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse AcsEvent response", e);
        }
    }

    public record AcsEventItem(
            long serialNo,
            int majorEventType,
            int subEventType,
            String time,
            String name,
            String employeeNoString,
            String cardNo,
            String pictureURL,
            String currentVerifyMode,
            int picturesNumber
    ) {
    }
}
