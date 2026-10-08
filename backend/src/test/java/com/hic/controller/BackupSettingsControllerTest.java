package com.hic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.dto.BackupSettingsResponse;
import com.hic.repository.UserRepository;
import com.hic.service.BackupSettingsService;
import com.hic.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = BackupSettingsController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class BackupSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private BackupSettingsService backupSettingsService;

    @Test
    void getSettings_returnsStorageInformation() throws Exception {
        when(backupSettingsService.getSettings()).thenReturn(response());

        mockMvc.perform(get("/api/settings/backup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderPath").value("D:\\AttendraBackups\\daily"))
                .andExpect(jsonPath("$.data.currentDataBytes").value(3_300));
    }

    @Test
    void updateSettings_savesFolderAndEnabledState() throws Exception {
        when(backupSettingsService.updateSettings(true, "D:\\AttendraBackups\\daily"))
                .thenReturn(response());

        mockMvc.perform(put("/api/settings/backup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "enabled", true,
                                "folderPath", "D:\\AttendraBackups\\daily"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(backupSettingsService).updateSettings(true, "D:\\AttendraBackups\\daily");
    }

    @Test
    void updateSettings_missingFolder_returns400() throws Exception {
        mockMvc.perform(put("/api/settings/backup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    private BackupSettingsResponse response() {
        return new BackupSettingsResponse(
                true,
                "D:\\AttendraBackups\\daily",
                183,
                "SUCCESS",
                "2026-10-08T04:00:00+04:00",
                "Backup uğurla tamamlandı.",
                500,
                1_500,
                1_000,
                2_000,
                300,
                3_300);
    }
}
