package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 财务日志操作类型与数据库白名单对应。新增动作时必须同步数据库约束，确保所有财务操作都能准确审计。 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceOperationTypeEnum {

    /**
     * 生成正常应收 / 应付：业务事实（签收 / 收货确认）在财务域的派生写，不挂权限点。
     */
    GENERATE("生成"),

    /**
     * 生成红字应收 / 登记红字应付。红字应收是自动派生（退货批准触发），红字应付是人工命令（{@code scm:finance:payable:red}）。
     */
    RED_GENERATE("红字生成"),

    /**
     * 登记收款（{@code scm:finance:receipt:add}）。
     */
    RECEIVE("收款登记"),

    /**
     * 登记付款（{@code scm:finance:payment:add}），含退款付款。
     */
    PAY("付款登记"),

    /**
     * 核销（{@code scm:finance:write-off:add}）。
     */
    WRITE_OFF("核销"),

    /**
     * 反向核销（{@code scm:finance:write-off:reverse}）。
     */
    WRITE_OFF_REVERSE("核销撤销"),

    /**
     * 反向收款（{@code scm:finance:receipt:reverse}，独立的破坏性权限）。
     */
    RECEIPT_REVERSE("收款反向"),

    /**
     * 反向付款（{@code scm:finance:payment:reverse}，独立的破坏性权限）。
     */
    PAYMENT_REVERSE("付款反向");

    private final String desc;
}
