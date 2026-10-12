package com.hic.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EmployeeRemovalDeviceClientTest {
    @Test
    void onlyCompleteValidLiveSnapshotCanConfirmAbsence() {
        for (String body : java.util.List.of("[]", "[{\"employeeNo\":\"400\"}]", "null", "{}",
                "[{}]", "[{\"employeeNo\":\"x\"},{\"employeeNo\":\"x\"}]")) {
            AtomicReference<MockRestServiceServer> server = new AtomicReference<>();
            var client = new EmployeeRemovalDeviceClient(new RestTemplateBuilder()
                    .additionalCustomizers(http -> server.set(MockRestServiceServer.bindTo(http).build())),
                    "http://bridge.test", "test-key");
            server.get().expect(requestTo("http://bridge.test/api/devices/101/users/reconciliation-snapshot"))
                    .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            if (body.equals("[]")) assertThat(client.absent(101L, "400")).isTrue();
            else if (body.equals("[{\"employeeNo\":\"400\"}]")) assertThat(client.absent(101L, "400")).isFalse();
            else assertThatThrownBy(() -> client.absent(101L, "400")).isInstanceOf(IllegalStateException.class);
            server.get().verify();
        }
    }
    @Test
    void usesBridgeIdentityApiKeyAndEncodedTerminalNumber() {
        AtomicReference<MockRestServiceServer> server = new AtomicReference<>();
        var client = new EmployeeRemovalDeviceClient(new RestTemplateBuilder()
                .additionalCustomizers(http -> server.set(MockRestServiceServer.bindTo(http).build())),
                "http://bridge.test:8081/", "test-key");
        server.get().expect(requestTo("http://bridge.test:8081/api/devices/101"))
                .andExpect(header("X-API-Key", "test-key"))
                .andRespond(withSuccess("{\"id\":101,\"ip\":\"192.0.2.10\",\"name\":\"Test\"}", MediaType.APPLICATION_JSON));
        server.get().expect(requestTo("http://bridge.test:8081/api/devices/101/status"))
                .andRespond(withSuccess("{\"id\":101,\"online\":true}", MediaType.APPLICATION_JSON));
        server.get().expect(requestTo("http://bridge.test:8081/api/devices/101/users/by-employee-no/A%2F4"))
                .andExpect(method(HttpMethod.DELETE)).andExpect(header("X-API-Key", "test-key"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThat(client.device(101L).getIp()).isEqualTo("192.0.2.10");
        assertThat(client.status(101L).isOnline()).isTrue();
        assertThatThrownBy(() -> client.delete(101L, "A/4"))
                .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
        server.get().verify();
    }
}
