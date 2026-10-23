package com.xsy.scm.common.scope;

import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.exception.BusinessException;

/**
 * 数据存在但当前用户无权访问时用这个异常，对外统一返回无权限码，别让人看出对象存不存在。
 */
public class ScmDataScopeException extends BusinessException {

    /** 消息与 {@code ScmExceptionHandler} 返回的一致，日志和客户端看到同一句话。 */
    public ScmDataScopeException() {
        super(UserErrorCode.NO_PERMISSION.getMsg());
    }
}
