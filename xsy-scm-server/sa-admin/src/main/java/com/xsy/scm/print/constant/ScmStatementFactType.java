package com.xsy.scm.print.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 对账单来源事实类型的中文叫法。
 *
 * <p>
 * 事实类型来自 {@code xsy_customer_statement_source.fact_type}，由财务各表汇总而来，没有统一枚举； 这里为对账导出和打印共用中文标签，未知类型原样输出，不猜测含义。
 */
@Getter
@RequiredArgsConstructor
public enum ScmStatementFactType {

    /** 销售应收。 */
    RECEIVABLE("销售应收"),

    /** 退货红字。 */
    RED("退货扣减"),

    /** 实际收款。 */
    RECEIPT("实际收款"),

    /** 反向收款。 */
    RECEIPT_REVERSE("撤销收款记录"),

    /** 核销分配。 */
    WRITE_OFF("账款抵扣"),

    /** 反向核销。 */
    WRITE_OFF_REVERSE("撤销抵扣"),

    /** 抹零：对账时主动免掉的零头，冲减应收但没有资金流入。 */
    WAIVER("抹零"),

    /** 反向抹零：把已免掉的零头放回未结应收。 */
    WAIVER_REVERSE("撤销抹零"),

    /** 实际退款。 */
    REFUND("实际退款"),

    /** 反向退款。 */
    REFUND_REVERSE("撤销退款记录"),
    POSTING("外部账单"),
    PAYABLE("采购应付"),
    PAYMENT("实际付款"),
    PAYMENT_REVERSE("撤销付款记录");

    private final String desc;

    /**
     * 按事实类型取中文标签；空值返回空字符串，未知类型保留原值。
     */
    public static String labelOf(String factType) {
        for (ScmStatementFactType type : values()) {
            if (type.name().equals(factType)) {
                return type.getDesc();
            }
        }
        return factType == null ? "" : factType;
    }

    public static String supplierLabelOf(String factType) {
        if (RED.name().equals(factType)) {
            return "应付扣减";
        }
        if (WRITE_OFF.name().equals(factType)) {
            return "付款抵扣";
        }
        return labelOf(factType);
    }
}
