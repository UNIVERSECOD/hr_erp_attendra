package com.hic.controller;

import com.hic.repository.UserRepository;
import com.hic.service.IsapiEmployeeAccessEventService;
import com.hic.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = IsapiAccessControlController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class}
)
class IsapiAccessControlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IsapiEmployeeAccessEventService isapiEmployeeAccessEventService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @Test
    void userInfoRecord_routesPayloadToService() throws Exception {
        String payload = """
                {
                  "UserInfo": {
                    "employeeNo": "0c3d0ca294e38f5cbd35eb2ba94dcfa4",
                    "userType": "normal",
                    "doorRight": "1",
                    "Valid": {
                      "enable": true,
                      "beginTime": "2026-05-18T00:00:00",
                      "endTime": "2036-05-17T23:59:59"
                    },
                    "RightPlan": [
                      {
                        "doorNo": 1,
                        "planTemplateNo": "1"
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(post("/ISAPI/AccessControl/UserInfo/Record")
                        .queryParam("format", "json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted());

        verify(isapiEmployeeAccessEventService).routeUserInfoRecord(argThat(record ->
                record != null
                        && record.getUserInfo() != null
                        && "0c3d0ca294e38f5cbd35eb2ba94dcfa4".equals(record.getUserInfo().getEmployeeNo())
                        && "normal".equals(record.getUserInfo().getUserType())
                        && record.getUserInfo().getRightPlan() != null
                        && record.getUserInfo().getRightPlan().size() == 1
        ));
    }
}
