package com.xsy.scm.print.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 对账单来源事实类型的中文叫法。
 *
 * <p>
 * 事实类型来自 {@code xsy_customer_statement_source.fact_type}，由财务各表汇总而来，没有统一枚举； 这里只给打印件用的中文标签，未知类型原样输出，不猜测含义。
 */
@Getter
@RequiredArgsConstructor
public enum ScmStatementFactType {

    /** 销售应收。 */
    RECEIVABLE("销售应收"),

    /** 退货红字。 */
    RED("退货红字"),

    /** 实际收款。 */
    RECEIPT("实际收款"),

    /** 反向收款。 */
    RECEIPT_REVERSE("反向收款"),

    /** 核销分配。 */
    WRITE_OFF("核销分配"),

    /** 反向核销。 */
    WRITE_OFF_REVERSE("反向核销"),

    /** 抹零：对账时主动免掉的零头，冲减应收但没有资金流入。 */
    WAIVER("抹零"),

    /** 反向抹零：把已免掉的零头放回未结应收。 */
    WAIVER_REVERSE("反向抹零"),

    /** 实际退款。 */
    REFUND("实际退款"),

    /** 反向退款。 */
    REFUND_REVERSE("反向退款");

    private final String desc;

    /**
     * 按事实类型取中文标签；未知类型返回 {@code null}，由调用方原样输出。
     */
    public static String labelOf(String factType) {
        for (ScmStatementFactType type : values()) {
            if (type.name().equals(factType)) {
                return type.getDesc();
            }
        }
        return null;
    }
}
