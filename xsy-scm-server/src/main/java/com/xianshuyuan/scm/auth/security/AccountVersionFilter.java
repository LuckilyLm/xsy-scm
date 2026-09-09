package com.xianshuyuan.scm.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.AuthErrorCodes;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AccountVersionFilter extends OncePerRequestFilter {

    private final SystemUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public AccountVersionFilter(SystemUserMapper userMapper, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SystemUserDetails userDetails)) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthenticatedUser principal = userDetails.getUser();
        SystemUserEntity currentUser = userMapper.selectActiveSecurityStateById(principal.userId());
        boolean valid = currentUser != null
                && "ENABLED".equals(currentUser.getStatus())
                && java.util.Objects.equals(currentUser.getAuthVersion(), principal.authVersion());
        if (valid) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (Boolean.TRUE.equals(currentUser.getMustChangePassword())
                    && !passwordRecoveryRoute(request.getMethod(), path)) {
                SecurityErrorResponseWriter.write(response, objectMapper, AuthErrorCodes.PASSWORD_CHANGE_REQUIRED);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityErrorResponseWriter.write(response, objectMapper, AuthErrorCodes.SESSION_INVALID);
    }

    private boolean passwordRecoveryRoute(String method, String path) {
        return ("GET".equals(method) && java.util.Set.of("/api/auth/me", "/api/auth/csrf").contains(path))
                || ("POST".equals(method) && java.util.Set.of("/api/auth/change-password", "/api/auth/logout", "/api/auth/login").contains(path));
    }
}
