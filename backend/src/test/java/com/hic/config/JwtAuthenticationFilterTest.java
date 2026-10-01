package com.hic.config;

import com.hic.model.User;
import com.hic.repository.UserRepository;
import com.hic.util.JwtUtil;
import com.hic.util.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userRepository);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void accessTokenUsesCurrentDatabaseRoleAndTenant() throws Exception {
        User user = new User();
        user.setId(9L);
        user.setTenantId(4L);
        user.setUsername("admin");
        user.setUserType(User.UserType.OFFICE_HR);

        when(jwtUtil.validateToken("access-token")).thenReturn(true);
        when(jwtUtil.isAccessToken("access-token")).thenReturn(true);
        when(jwtUtil.extractUsername("access-token")).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        AtomicReference<Authentication> authentication = new AtomicReference<>();
        AtomicReference<Long> tenantId = new AtomicReference<>();
        FilterChain chain = (request, response) -> {
            authentication.set(SecurityContextHolder.getContext().getAuthentication());
            tenantId.set(TenantContext.getTenantId());
        };

        filter.doFilter(requestWithBearer("access-token"), new MockHttpServletResponse(), chain);

        assertThat(authentication.get()).isNotNull();
        assertThat(authentication.get().getName()).isEqualTo("admin");
        assertThat(authentication.get().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_OFFICE_HR");
        assertThat(tenantId.get()).isEqualTo(4L);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void refreshTokenDoesNotAuthenticateApiRequest() throws Exception {
        when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
        when(jwtUtil.isAccessToken("refresh-token")).thenReturn(false);

        AtomicReference<Authentication> authentication = new AtomicReference<>();
        FilterChain chain = (request, response) ->
                authentication.set(SecurityContextHolder.getContext().getAuthentication());

        filter.doFilter(requestWithBearer("refresh-token"), new MockHttpServletResponse(), chain);

        assertThat(authentication.get()).isNull();
        verify(userRepository, never()).findByUsername(anyString());
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
