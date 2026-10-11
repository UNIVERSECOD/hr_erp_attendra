package com.hic.service;

import com.hic.model.Employee;
import org.junit.jupiter.api.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DeviceReconciliationClientTest {
    DeviceReconciliationClient client;
    MockRestServiceServer server;
    @BeforeEach void setup() {
        client = new DeviceReconciliationClient(new RestTemplateBuilder().additionalCustomizers(
                http -> server = MockRestServiceServer.bindTo(http).build()),
                mock(EmployeeFaceImageService.class), "http://mock", "test-key");
    }
    @Test void addsEmployeeUsingTerminalNumberAndAcceptsMissingGender() {
        Employee e = new Employee(); e.setEmployeeId("HR-42"); e.setDeviceEmployeeNo("RAW-7"); e.setFirstName("Test"); e.setLastName("User");
        server.expect(requestTo("http://mock/api/devices/900/users")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "test-key")).andExpect(jsonPath("$.employeeNo").value("RAW-7"))
                .andRespond(withSuccess("{\"id\":17}", MediaType.APPLICATION_JSON));
        assertThat(client.add(900, e)).isEqualTo(17L); server.verify();
    }
    @Test void deletesThroughDeviceScopedRouteWithoutRemovingSharedCache() {
        server.expect(requestTo("http://mock/api/devices/900/users/reconciliation/by-employee-no/RAW-7"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());
        client.remove(900, "RAW-7"); server.verify();
    }
    @Test void rejectsInvalidSnapshotAndUsesStrictEndpoint() {
        server.expect(requestTo("http://mock/api/devices/900/users/reconciliation-snapshot"))
                .andRespond(withSuccess("[{\"employeeNo\":\"1\"},{\"employeeNo\":\"1\"}]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.snapshot(900)).isInstanceOf(IllegalStateException.class); server.verify();
    }
}
