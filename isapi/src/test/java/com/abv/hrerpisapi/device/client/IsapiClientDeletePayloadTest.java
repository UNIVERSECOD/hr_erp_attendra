package com.abv.hrerpisapi.device.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IsapiClientDeletePayloadTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void deviceUserDelete_matchesTerminalProtocol() throws Exception {
        JsonNode payload = OM.readTree(IsapiClient.buildDeviceUserDeleteBody("1234"));

        assertThat(IsapiClient.USER_INFO_DELETE_ENDPOINT)
                .isEqualTo("/ISAPI/AccessControl/UserInfo/Delete?format=json");
        assertThat(payload.path("UserInfoDelCond").path("EmployeeNoList").size()).isEqualTo(1);
        assertThat(payload.path("UserInfoDelCond")
                .path("EmployeeNoList")
                .path(0)
                .path("employeeNo")
                .asText()).isEqualTo("1234");
    }

    @Test
    void faceDelete_matchesFdSearchProtocol() throws Exception {
        JsonNode payload = OM.readTree(IsapiClient.buildFaceDeleteBody("1234"));

        assertThat(IsapiClient.FACE_DELETE_ENDPOINT)
                .isEqualTo("/ISAPI/Intelligent/FDLib/FDSearch/Delete?format=json&FDID=1&faceLibType=blackFD");
        assertThat(payload.path("FPID").size()).isEqualTo(1);
        assertThat(payload.path("FPID").path(0).path("value").asText()).isEqualTo("1234");
    }
}
