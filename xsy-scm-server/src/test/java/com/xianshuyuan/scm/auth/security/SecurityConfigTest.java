package com.xianshuyuan.scm.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.controller.AuthController;
import com.xianshuyuan.scm.auth.service.AuthIdentityService;
import com.xianshuyuan.scm.auth.service.LoginLogService;
import com.xianshuyuan.scm.auth.service.PasswordManagementService;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private AuthIdentityService identityService;

    @MockitoBean
    private SystemUserMapper userMapper;

    @MockitoBean
    private DatabaseLoginAuthenticationProvider loginAuthenticationProvider;

    @MockitoBean
    private PasswordManagementService passwordManagementService;

    @MockitoBean
    private LoginLogService loginLogService;

    @Test
    void csrfEndpointIsPublicAndReturnsStandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"));
    }

    @Test
    void protectedApiReturnsStandard401Envelope() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40102))
            .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void loginWithoutCsrfReturnsStable403Envelope() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"password\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40304));
    }

    @Test
    @WithMockUser
    void logoutRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40304));
    }

    @Test
    @WithMockUser
    void logoutWithCsrfSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }
}
