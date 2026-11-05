package com.xsy.scm.mall.account.support;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.base.common.util.XsyRequestUtil;

/**
 * 商城客户请求上下文：统一取当前客户身份与「临时密码未改」守卫。
 */
public final class MallRequestSupport {
    private MallRequestSupport() {
    }

    public static RequestMallCustomer current() {
        return (RequestMallCustomer) XsyRequestUtil.getRequestUser();
    }

    /**
     * 当前认证会话仍要求修改临时密码时，除改密与资料接口外一律拒绝。 微信手机号会话的豁免由服务端身份查询结果，不修改账号的初始密码状态。
     */
    public static void requirePasswordChanged() {
        if (Boolean.TRUE.equals(current().getFirstPasswordChange())) {
            throw new ScmBusinessException(MallErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
    }
}
