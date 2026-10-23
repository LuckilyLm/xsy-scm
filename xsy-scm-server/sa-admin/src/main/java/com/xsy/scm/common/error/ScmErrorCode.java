package com.xsy.scm.common.error;

import net.lab1024.sa.base.common.code.ErrorCode;

/**
 * SCM 错误码沿用 SmartAdmin 的响应结构，但不走它的统一异常捕获。
 */
public interface ScmErrorCode extends ErrorCode {

    @Override
    default String getLevel() {
        return LEVEL_USER;
    }

}
