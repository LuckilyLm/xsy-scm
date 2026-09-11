package com.xianshuyuan.scm.mall.service;

import com.xianshuyuan.scm.mall.dto.MallLoginRequest;
import com.xianshuyuan.scm.mall.security.MallCustomerPrincipal;
import com.xianshuyuan.scm.mall.vo.MallLoginResponse;
import com.xianshuyuan.scm.mall.vo.MallProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MallAuthService {

    private final MallSessionService sessions;

    public MallLoginResponse login(MallLoginRequest request, String userAgent) {
        MallSessionService.LoginResult result = sessions.login(request.username(), request.password(), userAgent);
        return new MallLoginResponse(result.token(), result.expiresAt(), profile(result.principal()));
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.revoke(token.trim());
        }
    }

    public MallProfileResponse profile(MallCustomerPrincipal principal) {
        return new MallProfileResponse(principal.accountId(), principal.customerId(), principal.customerCode(),
                principal.customerName(), principal.username(), principal.wechatBound());
    }
}
