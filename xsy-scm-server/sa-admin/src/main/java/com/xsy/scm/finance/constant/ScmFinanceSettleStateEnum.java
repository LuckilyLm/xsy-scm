package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 应付单与应收单的结算状态。
 *
 * <p>
 * 与查询表单 {@code settleState} 的取值一一对应，也是导出与列表筛选共用的白名单来源。 {@code desc} 供导出与页面展示使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceSettleStateEnum {

    /** 未结清。 */
    OPEN("未结清"),

    /** 部分结清。 */
    PARTIAL("部分结清"),

    /** 已结清。 */
    SETTLED("已结清");

    private final String desc;
}
