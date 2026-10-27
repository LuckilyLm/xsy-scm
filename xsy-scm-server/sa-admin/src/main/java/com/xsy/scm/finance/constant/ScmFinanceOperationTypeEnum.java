package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 财务日志的操作类型，与数据库 CHECK 约束对应。新增动作时要同步更新约束，保证所有财务操作都能审计。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceOperationTypeEnum {

    /**
     * 生成正常应收 / 应付：由签收、收货确认等业务数据派生写入，不挂权限点。
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
    PAYMENT_REVERSE("付款反向"),

    /**
     * 登记抹零（{@code scm:finance:waiver:add}）：冲减应收的减免事实，不是收款。
     */
    WAIVER_ADD("抹零登记"),

    /**
     * 反向抹零（{@code scm:finance:waiver:reverse}）：追加反向核销行，抹零事实置为已反向。
     */
    WAIVER_REVERSE("抹零反向");

    private final String desc;
}
