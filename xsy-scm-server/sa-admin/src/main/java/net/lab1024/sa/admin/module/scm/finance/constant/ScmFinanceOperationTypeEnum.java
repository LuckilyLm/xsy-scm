package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 财务操作日志的动作类型，对应 {@code finance_operation_log.operation_type} 的
 * {@code ck_finance_operation_log_type} 白名单。
 *
 * <p><b>这八个值就是 F1-1 的最终范围</b>：后续阶段只**使用**它们，不再扩充。
 * 确需新值时回到 {@code docs/decisions.md} 追加裁决并新开迁移改 CHECK，不在代码里就地加。
 *
 * <p>白名单存在的理由与 {@code ScmOrderOperationTypeEnum} 相同：日志类型一旦可以自由填写，
 * 「这个动作有没有留痕」就无法用一条 SQL 回答，而财务恰恰最需要这个回答。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceOperationTypeEnum {

    /**
     * 生成正常应收 / 应付：业务事实（签收 / 收货确认）在财务域的派生写，不挂权限点。
     */
    GENERATE("生成"),

    /**
     * 生成红字应收 / 登记红字应付。红字应收是自动派生（退货批准触发），
     * 红字应付是人工命令（{@code scm:finance:payable:red}）。
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
     * 反向核销（{@code scm:finance:write-off:reverse}，Q18）。
     */
    WRITE_OFF_REVERSE("核销撤销"),

    /**
     * 反向收款（{@code scm:finance:receipt:reverse}，D-3 的独立破坏性权限）。
     */
    RECEIPT_REVERSE("收款反向"),

    /**
     * 反向付款（{@code scm:finance:payment:reverse}，D-3 的独立破坏性权限）。
     */
    PAYMENT_REVERSE("付款反向");

    private final String desc;
}
