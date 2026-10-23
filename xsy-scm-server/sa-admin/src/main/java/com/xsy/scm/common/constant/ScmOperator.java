package com.xsy.scm.common.constant;

import net.lab1024.sa.base.common.util.SmartRequestUtil;

public final class ScmOperator {

    private ScmOperator() {
    }

    public static String current() {
        String operator = currentOrNull();
        if (operator == null) {
            throw new IllegalStateException("SCM mutation requires an authenticated operator");
        }
        return operator;
    }

    /**
     * 后台任务等没有登录上下文时可以返回空操作人。普通业务写入仍必须明确操作人，不能因此放宽权限检查。
     */
    public static String currentOrNull() {
        var user = SmartRequestUtil.getRequestUser();
        if (user == null || user.getUserId() == null) {
            return null;
        }
        return user.getUserType().getValue() + ":" + user.getUserId();
    }
}
