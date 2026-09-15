package net.lab1024.sa.admin.module.scm.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 跨 SCM 域共享的错误码。
 *
 * <p>W1 已在 {@code ProductErrorCode} 中定义过 {@code VERSION_CONFLICT(40921)}。W2 不修改已验收的
 * W1 代码，因此在这里重新声明一次同码值常量；两个枚举对外表现完全一致（前端与
 * {@code t_operate_log} 无法区分）。建议 W3 开始前做一次纯 Java 重构让 ProductErrorCode 指向本枚举，
 * 该重构不涉及 migration、不改变对外行为。
 */
@Getter
@RequiredArgsConstructor
public enum ScmCommonErrorCode implements ScmErrorCode {

    /** 乐观锁冲突：影响行数为 0，说明数据已被其他操作修改。 */
    VERSION_CONFLICT(40921, "数据已被其他操作修改，请刷新后重试"),

    /**
     * 请求参数不正确。
     *
     * <p>用于「非 MVC 入口」的解析失败——即参数已经进入 Service / Manager，不再经过
     * Bean Validation 的场景（内部调用、批量导入、工具类解析）。MVC 入口的校验失败仍由
     * SmartAdmin 的 {@code GlobalExceptionHandler} 以 30001 返回，SCM 不重复接管。
     */
    VALIDATION_ERROR(40000, "请求参数不正确");

    private final int code;
    private final String msg;
}
