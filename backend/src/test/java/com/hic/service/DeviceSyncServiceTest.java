package com.hic.service;

import com.hic.dto.DeviceSyncDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeviceSyncServiceTest {

    private DeviceSyncService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new DeviceSyncService(restTemplate);
        ReflectionTestUtils.setField(service, "isapiBaseUrl", "http://host.docker.internal:8080");
    }

    @Test
    void getAllDevices_mapsIsapiResponseToFrontendDto() {
        server.expect(requestTo("http://host.docker.internal:8080/api/devices"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":7,"ip":"10.10.10.10","username":"admin","name":"Main Gate","enabled":true,"running":false}]
                        """, MediaType.APPLICATION_JSON));

        List<DeviceSyncDTO.DeviceConfigDTO> result = service.getAllDevices(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(7L);
        assertThat(result.get(0).getDeviceId()).isEqualTo("7");
        assertThat(result.get(0).getDeviceIp()).isEqualTo("10.10.10.10");
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
        server.verify();
    }

    @Test
    void createDevice_sendsMappedPayloadToIsapi() {
        server.expect(requestTo("http://host.docker.internal:8080/api/devices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"id":3,"ip":"192.168.1.11","username":"admin","name":"Office","enabled":false,"running":false}
                        """, MediaType.APPLICATION_JSON));

        DeviceSyncDTO.DeviceConfigDTO request = new DeviceSyncDTO.DeviceConfigDTO();
        request.setDeviceIp("192.168.1.11");
        request.setUsername("admin");
        request.setPassword("12345");
        request.setDeviceName("Office");
        request.setStatus("INACTIVE");

        DeviceSyncDTO.DeviceConfigDTO result = service.createDevice(request);

        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.getStatus()).isEqualTo("INACTIVE");
        server.verify();
    }

    @Test
    void syncDevice_forwardsToImmediateSyncEndpoint() {
        server.expect(requestTo("http://host.docker.internal:8080/api/devices/9/sync"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success":true,"message":"Device history synchronized","recordsSynced":3}
                        """, MediaType.APPLICATION_JSON));

        DeviceSyncDTO.SyncResultDTO result = service.syncDevice(9L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Device history synchronized");
        assertThat(result.getRecordsSynced()).isEqualTo(3);
        server.verify();
    }
}
