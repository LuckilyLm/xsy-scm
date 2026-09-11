package com.xianshuyuan.scm.mall.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.mall.service.MallErrorCodes;
import com.xianshuyuan.scm.mall.service.MallSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 商城客户令牌认证。与后台 Session/CSRF 体系并存，只作用于 /api/mall/**。
 */
@RequiredArgsConstructor
public class MallAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-Mall-Token";

    private final MallSessionService sessions;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }
        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        Optional<MallCustomerPrincipal> principal = sessions.resolve(token.trim());
        if (principal.isEmpty()) {
            SecurityContextHolder.clearContext();
            MallSecurityErrorWriter.write(response, objectMapper, MallErrorCodes.TOKEN_INVALID);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new MallCustomerAuthentication(principal.get()));
        chain.doFilter(request, response);
    }
}
