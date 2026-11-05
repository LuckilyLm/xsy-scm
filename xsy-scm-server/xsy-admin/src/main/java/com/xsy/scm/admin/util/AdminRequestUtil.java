package com.xsy.scm.admin.util;

import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.base.common.domain.RequestUser;
import com.xsy.scm.base.common.util.XsyRequestUtil;

/**
 * admin 端的请求工具类
 *
 */
public final class AdminRequestUtil {

    public static RequestEmployee getRequestUser() {
        return (RequestEmployee) XsyRequestUtil.getRequestUser();
    }

    public static Long getRequestUserId() {
        RequestUser requestUser = getRequestUser();
        return null == requestUser ? null : requestUser.getUserId();
    }

}
