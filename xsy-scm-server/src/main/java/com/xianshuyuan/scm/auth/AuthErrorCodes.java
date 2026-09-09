package com.xianshuyuan.scm.auth;

import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public final class AuthErrorCodes {

    public static final ErrorCode INVALID_CREDENTIALS =
            new ErrorCode(40101, HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    public static final ErrorCode LOGIN_REQUIRED =
            new ErrorCode(40102, HttpStatus.UNAUTHORIZED, "请先登录");
    public static final ErrorCode SESSION_INVALID =
            new ErrorCode(40103, HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录");
    public static final ErrorCode ACCOUNT_DISABLED =
            new ErrorCode(40301, HttpStatus.FORBIDDEN, "账号已停用");
    public static final ErrorCode ACCOUNT_LOCKED =
            new ErrorCode(40302, HttpStatus.FORBIDDEN, "账号已锁定，请稍后重试");
    public static final ErrorCode PERMISSION_DENIED =
            new ErrorCode(40303, HttpStatus.FORBIDDEN, "无权执行此操作");
    public static final ErrorCode CSRF_INVALID =
            new ErrorCode(40304, HttpStatus.FORBIDDEN, "页面安全令牌已失效，请刷新后重试");
    public static final ErrorCode PASSWORD_CHANGE_REQUIRED =
            new ErrorCode(40305, HttpStatus.FORBIDDEN, "请先修改密码后再继续操作");

    private AuthErrorCodes() {
    }
}
