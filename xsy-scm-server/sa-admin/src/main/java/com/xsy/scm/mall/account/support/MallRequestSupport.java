package com.xsy.scm.mall.account.support;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import net.lab1024.sa.base.common.util.SmartRequestUtil;

/** 商城客户请求上下文：统一取当前客户身份与「临时密码未改」守卫。 */
public final class MallRequestSupport {
    private MallRequestSupport() {
    }

    public static RequestMallCustomer current() {
        return (RequestMallCustomer) SmartRequestUtil.getRequestUser();
    }

    /** 首次登录未修改临时密码前，除改密与资料接口外一律拒绝。 */
    public static void requirePasswordChanged() {
        if (Boolean.TRUE.equals(current().getFirstPasswordChange())) {
            throw new ScmBusinessException(MallErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
    }
}
