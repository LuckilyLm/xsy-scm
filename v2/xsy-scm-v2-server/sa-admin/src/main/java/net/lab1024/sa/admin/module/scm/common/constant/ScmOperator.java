package net.lab1024.sa.admin.module.scm.common.constant;

import net.lab1024.sa.base.common.util.SmartRequestUtil;

public final class ScmOperator {
    private ScmOperator() { }
    public static String current() {
        var user = SmartRequestUtil.getRequestUser();
        if (user == null || user.getUserId() == null) {
            throw new IllegalStateException("SCM mutation requires an authenticated operator");
        }
        return user.getUserType().getValue() + ":" + user.getUserId();
    }
}
