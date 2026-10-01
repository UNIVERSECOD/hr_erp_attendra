package com.hic.config;

import com.hic.controller.EventReadController;
import com.hic.model.User;
import com.hic.repository.UserRepository;
import com.hic.service.IsapiProxyService;
import com.hic.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EventReadController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IsapiProxyService isapiProxyService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @Test
    void protectedEndpointWithoutAccessTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/punches"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void refreshTokenCannotAuthenticateProtectedEndpoint() throws Exception {
        when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
        when(jwtUtil.isAccessToken("refresh-token")).thenReturn(false);

        mockMvc.perform(get("/api/punches")
                        .header("Authorization", "Bearer refresh-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeRoleCannotReadRawDeviceEvents() throws Exception {
        mockAuthenticatedUser(User.UserType.EMPLOYEE);

        mockMvc.perform(get("/api/punches")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Bu əməliyyat üçün icazəniz yoxdur"));
    }

    @Test
    void hrRoleCanReadRawDeviceEvents() throws Exception {
        mockAuthenticatedUser(User.UserType.OFFICE_HR);
        when(isapiProxyService.forward(
                eq(HttpMethod.GET), eq("/api/punches"), any(), isNull()))
                .thenReturn(ResponseEntity.ok("[]"));

        mockMvc.perform(get("/api/punches")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    private void mockAuthenticatedUser(User.UserType userType) {
        User user = new User();
        user.setId(1L);
        user.setTenantId(1L);
        user.setUsername("tester");
        user.setUserType(userType);

        when(jwtUtil.validateToken("access-token")).thenReturn(true);
        when(jwtUtil.isAccessToken("access-token")).thenReturn(true);
        when(jwtUtil.extractUsername("access-token")).thenReturn("tester");
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
    }
}
