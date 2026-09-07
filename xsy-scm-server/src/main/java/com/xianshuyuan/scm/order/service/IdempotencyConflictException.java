package com.xianshuyuan.scm.order.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public final class IdempotencyConflictException extends BusinessException {
    public IdempotencyConflictException() {
        super(new ErrorCode(40930, HttpStatus.CONFLICT, "幂等键已用于不同请求"));
    }
}
