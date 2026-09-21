package com.hic.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "loginMaxPerMinute", 1);
    }

    @Test
    void initialSetupStatus_getRequestsAreNotRateLimited() throws Exception {
        MockHttpServletResponse firstResponse = perform("GET", "/api/auth/initial-setup");
        MockHttpServletResponse secondResponse = perform("GET", "/api/auth/initial-setup");

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void initialSetup_postRequestsAreRateLimited() throws Exception {
        MockHttpServletResponse firstResponse = perform("POST", "/api/auth/initial-setup");
        MockHttpServletResponse secondResponse = perform("POST", "/api/auth/initial-setup");

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse perform(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
