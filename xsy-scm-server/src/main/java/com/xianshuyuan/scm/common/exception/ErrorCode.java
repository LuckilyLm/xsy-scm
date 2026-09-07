package com.xianshuyuan.scm.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Objects;

public record ErrorCode(int code, HttpStatus status, String message) {

    public static final ErrorCode VALIDATION_ERROR =
            new ErrorCode(40000, HttpStatus.BAD_REQUEST, "请求参数不正确");
    public static final ErrorCode DATA_CONFLICT =
            new ErrorCode(40900, HttpStatus.CONFLICT, "数据状态冲突");
    public static final ErrorCode INTERNAL_ERROR =
            new ErrorCode(50000, HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误");

    public ErrorCode {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
    }
}
