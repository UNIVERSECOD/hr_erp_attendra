package com.hic.controller;

import com.hic.repository.UserRepository;
import com.hic.service.IsapiProxyService;
import com.hic.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = EventReadController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class}
)
class EventReadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IsapiProxyService isapiProxyService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @Test
    void punches_proxiesToIsapi() throws Exception {
        when(isapiProxyService.forward(eq(HttpMethod.GET), eq("/api/punches"), any(HttpServletRequest.class), isNull()))
                .thenReturn(ResponseEntity.ok("[{\"id\":12}]"));

        mockMvc.perform(get("/api/punches").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":12}]"));

        verify(isapiProxyService).forward(eq(HttpMethod.GET), eq("/api/punches"), any(HttpServletRequest.class), isNull());
    }

    @Test
    void searchAcsEvents_proxiesPostBodyToIsapi() throws Exception {
        String body = "{\"AcsEventCond\":{\"searchID\":\"1\",\"maxResults\":10}}";
        String upstreamResponse = "{\"AcsEvent\":{\"InfoList\":[{\"serialNo\":9,\"pictureURL\":\"/pic.jpg\"}]}}";

        when(isapiProxyService.forward(eq(HttpMethod.POST), eq("/api/acs-events/search"), any(HttpServletRequest.class), eq(body)))
                .thenReturn(ResponseEntity.ok(upstreamResponse));

        mockMvc.perform(post("/api/acs-events/search")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().json(upstreamResponse));

        verify(isapiProxyService).forward(eq(HttpMethod.POST), eq("/api/acs-events/search"), any(HttpServletRequest.class), eq(body));
    }

    @Test
    void searchAcsEventImages_proxiesPostBodyToIsapi() throws Exception {
        String body = "{\"AcsEventCond\":{\"searchID\":\"1\",\"maxResults\":10}}";
        String upstreamResponse = "{\"AcsEvent\":{\"InfoList\":[{\"serialNo\":9,\"pictureURL\":\"/pic.jpg\"}]}}";

        when(isapiProxyService.forward(eq(HttpMethod.POST), eq("/api/acs-events/image-search"), any(HttpServletRequest.class), eq(body)))
                .thenReturn(ResponseEntity.ok(upstreamResponse));

        mockMvc.perform(post("/api/acs-events/image-search")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().json(upstreamResponse));

        verify(isapiProxyService).forward(eq(HttpMethod.POST), eq("/api/acs-events/image-search"), any(HttpServletRequest.class), eq(body));
    }
}
