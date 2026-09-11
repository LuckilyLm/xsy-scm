package com.xianshuyuan.scm.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.AuthErrorCodes;
import com.xianshuyuan.scm.auth.service.LoginLogService;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
@lombok.extern.slf4j.Slf4j
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            SecurityContextRepository securityContextRepository,
            SystemUserMapper userMapper,
            DatabaseLoginAuthenticationProvider loginAuthenticationProvider,
            LoginLogService loginLogs
    ) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName("XSRF-TOKEN");
        csrfTokenRepository.setHeaderName("X-XSRF-TOKEN");
        csrfTokenRepository.setCookiePath("/");

        http
                .authenticationProvider(loginAuthenticationProvider)
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/csrf",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/products/**", "/api/product-categories/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "product.read"))
                        .requestMatchers("/api/products/**", "/api/product-categories/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "product.manage"))
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/customers/**", "/api/customer-types/**", "/api/customer-agreement-prices/**",
                                "/api/agreement-prices/**", "/api/customer-type-prices/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "customer.read"))
                        .requestMatchers("/api/customers/**", "/api/customer-types/**",
                                "/api/customer-agreement-prices/**", "/api/agreement-prices/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "customer.manage"))
                        .requestMatchers("/api/customer-type-prices/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "customer.manage"))
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/orders/**", "/api/order-returns/**", "/api/order-refunds/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "order.read"))
                        .requestMatchers("/api/orders/**", "/api/order-returns/**", "/api/order-refunds/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "order.manage"))
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/suppliers/**", "/api/warehouses/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "supplier.read"))
                        .requestMatchers("/api/suppliers/**", "/api/warehouses/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "supplier.manage"))
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/purchase-demands/**", "/api/purchase-orders/**", "/api/purchase-receipts/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "purchase.read"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/purchase-receipts/*/putaway")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "purchase:receipt:putaway"))
                .requestMatchers("/api/purchase-demands/**", "/api/purchase-orders/**", "/api/purchase-receipts/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "purchase.manage"))
                        .requestMatchers("/api/inventories/**", "/api/inventory-movements/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "inventory.read"))
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/system/users", "/api/system/users/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:user:list"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/users")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:user:create"))
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/system/users/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:user:update"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/users/*/reset-password")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:user:reset-password"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/users/*/status")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:user:status"))
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/system/users/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:user:delete"))
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/system/users/*/roles")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:user:list"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/users/*/roles")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:user:assign-roles"))
                        .requestMatchers("/api/system/users/**").denyAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/system/departments", "/api/system/departments/*", "/api/system/departments/*/descendants")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:department:list"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/departments")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:department:create"))
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/system/departments/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:department:update"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/departments/*/status")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:department:status"))
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/system/departments/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:department:delete"))
                        .requestMatchers("/api/system/departments/**").denyAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/system/roles", "/api/system/roles/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:role:list"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/roles")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:role:create"))
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/system/roles/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:role:update"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/roles/*/status")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:role:status"))
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/system/roles/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:role:delete"))
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/system/roles/*/permissions")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:role:list"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/roles/*/permissions")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:role:assign-permissions"))
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/system/roles/*/menus")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:role:list"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/roles/*/menus")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:role:assign-menus"))
                        .requestMatchers("/api/system/roles/**").denyAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/system/menus", "/api/system/menus/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:menu:list"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/menus")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:menu:create"))
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/system/menus/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:menu:update"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/menus/*/status")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:menu:status"))
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/system/menus/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:menu:delete"))
                        .requestMatchers("/api/system/menus/**").denyAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/system/permissions", "/api/system/permissions/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:permission:list"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/permissions")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:permission:create"))
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/system/permissions/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:permission:update"))
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/system/permissions/*/status")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:permission:status"))
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/system/permissions/*")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:permission:delete"))
                        .requestMatchers("/api/system/permissions/**").denyAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/system/login-logs")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:login-log:list"))
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/system/operation-logs")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system:operation-log:list"))
                        .requestMatchers("/api/system/login-logs/**", "/api/system/operation-logs/**").denyAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/marketing/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "marketing.read"))
                        .requestMatchers("/api/marketing/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "marketing.manage"))
                        .requestMatchers("/api/system/**")
                        .access((authentication, context) -> AuthorityRules.hasAuthority(authentication, "system.manage"))
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().permitAll()
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                SecurityErrorResponseWriter.writeLoginRequired(response, objectMapper))
                        .accessDeniedHandler((request, response, exception) -> {
                            if (exception instanceof org.springframework.security.web.csrf.CsrfException) {
                                SecurityErrorResponseWriter.write(response, objectMapper, AuthErrorCodes.CSRF_INVALID);
                            } else {
                                SecurityErrorResponseWriter.writePermissionDenied(response, objectMapper);
                            }
                        })
                )
                .addFilterAfter(new AccountVersionFilter(userMapper, objectMapper), SecurityContextHolderFilter.class)
                .requestCache(RequestCacheConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("XSY_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null) {
                                try {
                                    Long userId = authentication.getPrincipal() instanceof SystemUserDetails details
                                            ? details.getUser().userId() : null;
                                    loginLogs.append(userId, authentication.getName(), "LOGOUT", null,
                                            request.getRemoteAddr(), request.getHeader("User-Agent"));
                                } catch (RuntimeException ignored) {
                                    // Logout remains successful when audit persistence is unavailable.
                                    log.warn("Logout audit persistence failed");
                                }
                            }
                            SecurityErrorResponseWriter.writeSuccess(response, objectMapper);
                        })
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
