package com.xsy.scm.finance.constant;

/**
 * 应付单与应收单的结算状态。
 *
 * <p>
 * 与查询表单 {@code settleState} 的取值一一对应，也是导出与列表筛选共用的白名单来源。
 */
public enum ScmFinanceSettleStateEnum {

    /** 未结清。 */
    OPEN,

    /** 部分结清。 */
    PARTIAL,

    /** 已结清。 */
    SETTLED
}
