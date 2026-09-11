package com.xianshuyuan.scm.mall.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.mall.service.MallErrorCodes;
import com.xianshuyuan.scm.mall.service.MallSessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * 商城独立安全链，仅拦截 /api/mall/**，不改动后台 Session/CSRF 体系。
 * 令牌在请求头中传递，不使用 Cookie，因此对该路径关闭 CSRF。
 */
@Configuration
public class MallSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain mallSecurityFilterChain(HttpSecurity http, MallSessionService sessions,
                                                       ObjectMapper objectMapper) throws Exception {
        http
                .securityMatcher("/api/mall/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(true))
                .requestCache(RequestCacheConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/mall/auth/login", "/api/mall/auth/wechat-login")
                        .permitAll()
                        .requestMatchers("/api/mall/**")
                        .hasAuthority(MallCustomerAuthentication.MALL_CUSTOMER_AUTHORITY)
                )
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) ->
                                MallSecurityErrorWriter.write(response, objectMapper, MallErrorCodes.LOGIN_REQUIRED)))
                .addFilterBefore(new MallAuthenticationFilter(sessions, objectMapper), AuthorizationFilter.class);

        return http.build();
    }
}
