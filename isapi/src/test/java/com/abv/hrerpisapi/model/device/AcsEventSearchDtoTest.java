package com.abv.hrerpisapi.model.device;

import com.abv.hrerpisapi.model.request.device.AcsEventSearchRequest;
import com.abv.hrerpisapi.model.response.device.AcsEventSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AcsEventSearchDtoTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void fromCondition_setsDefaultsWithoutPicEnable() throws Exception {
        AcsEventSearchRequest.AcsEventCondRequest cond = new AcsEventSearchRequest.AcsEventCondRequest(
                "",
                null,
                null,
                0,
                0,
                "2026-01-01T00:00:00+04:00",
                "2026-01-01T01:00:00+04:00",
                null,
                null,
                null
        );

        String json = AcsEventSearchRequest.fromCondition(cond);
        JsonNode acsEventCond = OM.readTree(json).path("AcsEventCond");

        assertThat(acsEventCond.path("searchID").asText()).isNotBlank();
        assertThat(acsEventCond.path("searchResultPosition").asInt()).isEqualTo(0);
        assertThat(acsEventCond.path("maxResults").asInt()).isEqualTo(50);
        assertThat(acsEventCond.path("major").asInt()).isEqualTo(0);
        assertThat(acsEventCond.path("minor").asInt()).isEqualTo(0);
        assertThat(acsEventCond.has("picEnable")).isFalse();
    }

    @Test
    void fromJson_mapsPictureUrl() {
        String body = """
                {
                  "AcsEvent": {
                    "searchID": "abc",
                    "responseStatusStrg": "OK",
                    "numOfMatches": 1,
                    "totalMatches": 1,
                    "InfoList": [
                      {
                        "serialNo": 10,
                        "major": 5,
                        "minor": 75,
                        "time": "2026-01-01T00:01:00+04:00",
                        "employeeNoString": "1001",
                        "cardNo": "C-1",
                        "pictureURL": "/ISAPI/AccessControl/AcsEvent/Pic?name=abc.jpg"
                      }
                    ]
                  }
                }
                """;

        AcsEventSearchResponse response = AcsEventSearchResponse.fromJson(body);

        assertThat(response.searchID()).isEqualTo("abc");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).pictureURL()).contains("AcsEvent/Pic");
    }
}
