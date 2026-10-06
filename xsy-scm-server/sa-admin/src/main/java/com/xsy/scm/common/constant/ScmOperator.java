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
     * 当前操作者，没有登录上下文时返回 {@code null} 而不是抛异常。
     *
     * <p>
     * 供<b>定时任务</b>这类无请求上下文的调用方使用：它们写的是系统产生的记录（例如预警通知的 去重行），把「没有登录人」当成异常会让整次扫描失败。业务写路径必须继续用 {@link #current()} ——
     * 那条规则不能被这里放宽。
     */
    public static String currentOrNull() {
        var user = SmartRequestUtil.getRequestUser();
        if (user == null || user.getUserId() == null) {
            return null;
        }
        return user.getUserType().getValue() + ":" + user.getUserId();
    }
}
