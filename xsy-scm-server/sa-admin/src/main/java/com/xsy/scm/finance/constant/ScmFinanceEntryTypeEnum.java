package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 财务正常单与红字单分别用 NORMAL 和 RED 表示，金额始终为正。红字是新增的冲减记录，不允许通过改原记录金额实现冲销。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceEntryTypeEnum {

    /**
     * 正常记录：应收来自已签收订单，应付来自已确认收货单。
     */
    NORMAL("正常"),

    /**
     * 红字记录：应收来自已批准退货（自动），应付来自手工登记。必须引用原单并填原因。
     */
    RED("红字");

    private final String desc;
}
