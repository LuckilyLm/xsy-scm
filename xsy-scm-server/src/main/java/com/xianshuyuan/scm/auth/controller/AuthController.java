package com.xianshuyuan.scm.auth.controller;

import com.xianshuyuan.scm.auth.AuthErrorCodes;
import com.xianshuyuan.scm.auth.dto.CurrentUserResponse;
import com.xianshuyuan.scm.auth.dto.LoginRequest;
import com.xianshuyuan.scm.auth.dto.ChangePasswordRequest;
import com.xianshuyuan.scm.auth.service.PasswordManagementService;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.AuthIdentityService;
import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AuthIdentityService identityService;
    private final PasswordManagementService passwords;

    public AuthController(
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            AuthIdentityService identityService,
            PasswordManagementService passwords
    ) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.identityService = identityService;
        this.passwords = passwords;
    }

    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ApiResponse.success(Map.of(
                "headerName", "X-XSRF-TOKEN",
                "parameterName", csrfToken.getParameterName()
        ));
    }

    @PostMapping("/login")
    public ApiResponse<CurrentUserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        try {
            var login = UsernamePasswordAuthenticationToken.unauthenticated(
                    request.username().trim(), request.password());
            login.setDetails(new com.xianshuyuan.scm.auth.security.LoginRequestDetails(
                    servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent")));
            Authentication authentication = authenticationManager.authenticate(login);
            sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, servletRequest, servletResponse);
            return ApiResponse.success(identityService.toResponse(authentication));
        } catch (DisabledException | LockedException | BadCredentialsException exception) {
            throw new BusinessException(AuthErrorCodes.INVALID_CREDENTIALS);
        }
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(Authentication authentication) {
        return ApiResponse.success(identityService.toResponse(authentication));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            Authentication authentication) {
        passwords.changeOwn(request, authentication);
        return ApiResponse.success(null);
    }
}
