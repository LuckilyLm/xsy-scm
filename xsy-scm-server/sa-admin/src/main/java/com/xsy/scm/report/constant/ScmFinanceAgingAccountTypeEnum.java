package com.xsy.scm.report.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 账龄分析统计的往来方向。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceAgingAccountTypeEnum {

    /** 应收：客户欠款。 */
    RECEIVABLE("应收"),

    /** 应付：欠供应商款项。 */
    PAYABLE("应付");

    private final String desc;
}
