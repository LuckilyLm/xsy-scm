package com.xsy.scm.common.exception;

import lombok.Getter;
import com.xsy.scm.common.error.ScmErrorCode;
import net.lab1024.sa.base.common.exception.BusinessException;

@Getter
public class ScmBusinessException extends BusinessException {

    private final ScmErrorCode errorCode;

    public ScmBusinessException(ScmErrorCode errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }
}
