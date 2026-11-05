package com.xsy.scm.common.exception;

import lombok.Getter;
import com.xsy.scm.common.error.ScmErrorCode;
import com.xsy.scm.base.common.exception.BusinessException;

@Getter
public class ScmBusinessException extends BusinessException {

    private final ScmErrorCode errorCode;

    public ScmBusinessException(ScmErrorCode errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    /**
     * 同一个错误码需要按上下文补充细节时使用；前端仍按 {@code errorCode} 判断处理方式。
     */
    public ScmBusinessException(ScmErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
