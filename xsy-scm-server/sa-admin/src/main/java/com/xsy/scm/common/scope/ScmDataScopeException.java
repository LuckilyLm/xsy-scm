package com.xsy.scm.common.scope;

import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.exception.BusinessException;

/**
 * 数据存在但当前用户无权访问时使用独立异常，对外返回统一无权限码，避免泄露对象是否存在。
 */
public class ScmDataScopeException extends BusinessException {

    /**
     * 消息与 {@code ScmExceptionHandler} 给出的信封同源：日志里的原因与客户端拿到的 {@code msg} 是同一句话，排查时不会两种说法各信一半。
     */
    public ScmDataScopeException() {
        super(UserErrorCode.NO_PERMISSION.getMsg());
    }
}
