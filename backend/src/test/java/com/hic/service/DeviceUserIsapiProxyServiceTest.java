package com.hic.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeviceUserIsapiProxyServiceTest {

    private final IsapiProxyService isapiProxyService = mock(IsapiProxyService.class);
    private final DeviceUserIsapiProxyService service = new DeviceUserIsapiProxyService(isapiProxyService);

    @Test
    void listUsers_forwardsToExpectedPath() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        service.listUsers(9L, request);

        verify(isapiProxyService).forward(HttpMethod.GET, "/api/devices/9/users", request, null);
    }

    @Test
    void uploadFace_forwardsMultipartToExpectedPath() {
        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();

        service.uploadFace(3L, 12L, request);

        verify(isapiProxyService).forwardMultipart("/api/devices/3/users/12/face", request);
    }
}
