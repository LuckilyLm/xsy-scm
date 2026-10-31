package com.xsy.scm.common.util;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import java.util.Locale;

/**
 * SCM 编号规则：业务单据号 = {@code XSY} + 雪花 ID（如 {@code XSY1723456789012345678}）；主数据编码 = 前缀 + 至少 6 位序号，不带日期。
 *
 * <p>
 * 单据号不再用「英文前缀 + 业务日期 + 序号」拼接：雪花 ID 全局唯一、按时间递增，省掉按日序列和时区处理；单据类型由各业务字段区分。 已生成的历史单号按原样保留，不重写。
 */
public final class ScmDocumentNumbers {

    private static final String DOCUMENT_PREFIX = "XSY";

    private ScmDocumentNumbers() {
    }

    /**
     * 下一个业务单据号：{@code XSY} + 雪花 ID。
     */
    public static String next() {
        return DOCUMENT_PREFIX + IdWorker.getIdStr();
    }

    /** 主数据编码（客户 / 供应商 / 仓库等）：没有业务日期，只拼前缀与序号。 */
    public static String masterCode(String prefix, long number) {
        return prefix + String.format(Locale.ROOT, "%06d", number);
    }
}
