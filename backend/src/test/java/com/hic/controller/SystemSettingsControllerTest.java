package com.hic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.dto.SystemSettingsResponse;
import com.hic.dto.UpdateSystemSettingsRequest;
import com.hic.repository.UserRepository;
import com.hic.service.SystemSettingsService;
import com.hic.util.JwtUtil;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SystemSettingsController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class SystemSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private SystemSettingsService systemSettingsService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getSettings_returnsCurrentTenantValue() throws Exception {
        when(systemSettingsService.getSettings(7L)).thenReturn(new SystemSettingsResponse(10));

        mockMvc.perform(get("/api/settings/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.syncIntervalMinutes").value(10));
    }

    @Test
    void updateSettings_validRequest_usesCurrentTenant() throws Exception {
        UpdateSystemSettingsRequest request = new UpdateSystemSettingsRequest();
        request.setSyncIntervalMinutes(30);
        when(systemSettingsService.updateSettings(7L, 30)).thenReturn(new SystemSettingsResponse(30));

        mockMvc.perform(put("/api/settings/system")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncIntervalMinutes").value(30));

        verify(systemSettingsService).updateSettings(7L, 30);
    }

    @Test
    void updateSettings_missingInterval_returns400() throws Exception {
        mockMvc.perform(put("/api/settings/system")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
