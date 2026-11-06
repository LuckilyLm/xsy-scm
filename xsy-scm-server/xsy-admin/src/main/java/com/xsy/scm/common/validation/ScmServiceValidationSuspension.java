package com.xsy.scm.common.validation;

import java.util.function.Supplier;

/** 内部导出等可信调用可临时跳过服务入口校验。 */
public final class ScmServiceValidationSuspension {

    private static final ThreadLocal<Boolean> SUSPENDED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ScmServiceValidationSuspension() {
    }

    /** 在当前线程执行 action，期间服务入口校验不生效。 */
    public static <T> T run(Supplier<T> action) {
        SUSPENDED.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            SUSPENDED.set(Boolean.FALSE);
        }
    }

    static boolean suspended() {
        return SUSPENDED.get();
    }
}
