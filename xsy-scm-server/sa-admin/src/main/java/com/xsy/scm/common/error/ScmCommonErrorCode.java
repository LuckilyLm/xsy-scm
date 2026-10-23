package com.xsy.scm.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * SCM 各模块复用统一错误码；版本冲突等历史重复码暂保留兼容，后续调整要保证对外错误码不变。
 */
@Getter
@RequiredArgsConstructor
public enum ScmCommonErrorCode implements ScmErrorCode {

    /**
     * 乐观锁冲突：影响行数为 0，说明数据已被其他操作修改。
     */
    VERSION_CONFLICT(40921, "数据已被其他操作修改，请刷新后重试"),

    /**
     * 非 MVC 入口的参数解析错误使用 SCM 通用参数错误；普通接口入参校验仍由框架统一处理。
     */
    VALIDATION_ERROR(40000, "请求参数不正确"),

    IDEMPOTENCY_KEY_REQUIRED(40069, "Idempotency-Key 不能为空"),

    IDEMPOTENCY_KEY_INVALID(40070, "Idempotency-Key 长度不能超过 200 个字符"),

    IDEMPOTENCY_CONFLICT(40966, "相同幂等键的请求内容不一致");

    private final int code;

    private final String msg;
}
