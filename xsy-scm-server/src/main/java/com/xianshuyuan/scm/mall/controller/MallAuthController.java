package com.xianshuyuan.scm.mall.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.mall.dto.MallLoginRequest;
import com.xianshuyuan.scm.mall.dto.MallWechatLoginRequest;
import com.xianshuyuan.scm.mall.security.MallAuthenticationFilter;
import com.xianshuyuan.scm.mall.security.MallCustomerPrincipal;
import com.xianshuyuan.scm.mall.security.MallPrincipalResolver;
import com.xianshuyuan.scm.mall.service.MallAuthService;
import com.xianshuyuan.scm.mall.service.MallErrorCodes;
import com.xianshuyuan.scm.mall.vo.MallLoginResponse;
import com.xianshuyuan.scm.mall.vo.MallProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/mall/auth")
public class MallAuthController {

    private final MallAuthService auth;

    @PostMapping("/login")
    public ApiResponse<MallLoginResponse> login(@Valid @RequestBody MallLoginRequest request,
                                                @RequestHeader(value = "User-Agent", required = false)
                                                String userAgent) {
        return ApiResponse.success(auth.login(request, userAgent));
    }

    /**
     * 微信登录占位。凭证交换与绑定规则尚未确认，先固定契约，避免前端自由发挥。
     */
    @PostMapping("/wechat-login")
    public ApiResponse<MallLoginResponse> wechatLogin(@Valid @RequestBody MallWechatLoginRequest request) {
        throw new BusinessException(MallErrorCodes.WECHAT_LOGIN_UNAVAILABLE);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = MallAuthenticationFilter.TOKEN_HEADER, required = false)
                                    String token) {
        auth.logout(token);
        return ApiResponse.success(null);
    }

    @GetMapping("/profile")
    public ApiResponse<MallProfileResponse> profile(Authentication authentication) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(auth.profile(principal));
    }
}
