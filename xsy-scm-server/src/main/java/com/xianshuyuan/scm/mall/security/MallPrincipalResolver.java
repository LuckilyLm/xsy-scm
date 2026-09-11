package com.xianshuyuan.scm.mall.security;

import com.xianshuyuan.scm.mall.service.MallErrorCodes;
import com.xianshuyuan.scm.common.exception.BusinessException;
import org.springframework.security.core.Authentication;

/**
 * 从当前认证中解析商城客户身份。客户身份缺失时直接拒绝，避免退化为匿名访问。
 */
public final class MallPrincipalResolver {

    private MallPrincipalResolver() {
    }

    public static MallCustomerPrincipal require(Authentication authentication) {
        if (authentication instanceof MallCustomerAuthentication mall
                && mall.customer() != null) {
            return mall.customer();
        }
        if (authentication != null
                && authentication.getPrincipal() instanceof MallCustomerPrincipal principal) {
            return principal;
        }
        throw new BusinessException(MallErrorCodes.LOGIN_REQUIRED);
    }
}
