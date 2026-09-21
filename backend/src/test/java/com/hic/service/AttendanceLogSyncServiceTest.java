package com.hic.service;

import com.hic.dto.AttendanceLogSyncDTO;
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

class AttendanceLogSyncServiceTest {

    private AttendanceLogSyncService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        com.hic.repository.EmployeeRepository employeeRepository = org.mockito.Mockito.mock(com.hic.repository.EmployeeRepository.class);
        com.hic.repository.DeviceConfigRepository deviceConfigRepository = org.mockito.Mockito.mock(com.hic.repository.DeviceConfigRepository.class);
        service = new AttendanceLogSyncService(restTemplate, employeeRepository, deviceConfigRepository);
        ReflectionTestUtils.setField(service, "isapiBaseUrl", "http://host.docker.internal:8080");
    }

    @Test
    void getAttendanceLogs_mapsIsapiPunchesResponse() {
        server.expect(requestTo("http://host.docker.internal:8080/api/punches"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":15,"deviceId":5,"employeeNo":"E-001","punchTime":"2026-05-08T10:20:30Z","rawEventId":42}]
                        """, MediaType.APPLICATION_JSON));

        List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> result = service.getAttendanceLogs(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(15L);
        assertThat(result.get(0).getDeviceId()).isEqualTo(5L);
        assertThat(result.get(0).getEmployeeNo()).isEqualTo("E-001");
        assertThat(result.get(0).getRawEventId()).isEqualTo(42L);
        assertThat(result.get(0).getPunchTime()).isNotNull();
        server.verify();
    }

    @Test
    void getAttendanceLogs_includesOptionalQueryParams() {
        server.expect(requestTo("http://host.docker.internal:8080/api/punches?deviceId=9&employeeNo=123&limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> result = service.getAttendanceLogs(9L, " 123 ", 100);

        assertThat(result).isEmpty();
        server.verify();
    }
}
