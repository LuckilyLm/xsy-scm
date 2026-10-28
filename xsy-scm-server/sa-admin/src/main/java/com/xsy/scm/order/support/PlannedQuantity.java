package com.xsy.scm.order.support;

import java.math.BigDecimal;

/**
 * 计划数量：已称重取实发量，还没称重就取订购量。
 *
 * <p>
 * 非标行允许不填实重就确认（称重发生在分拣台），确认、授信、分拣排计划、采购需求这些「先按订单做计划」的环节都必须按同一口径取数： 有实发量用实发量，没有就用订购量。 各自再写一遍
 * {@code actual != null ? actual : ordered} 迟早会漏一个，漏掉的那一处不是少算需求就是直接空指针。
 *
 * <p>
 * 这<b>不是</b>结算口径：结算金额仍以 {@code actual_quantity} 为准，称重后由 {@code OrderActualQuantitySyncService} 回写并重算。
 */
public final class PlannedQuantity {

    private PlannedQuantity() {
    }

    /**
     * @param actualQuantity
     *            实发量，未称重时为 {@code null}
     * @param orderedQuantity
     *            订购量，必须不为空
     * @return 实发量大于 0 时取实发量，否则取订购量；两者都缺失时返回订购量（可能是 {@code null}），由调用方决定怎么拒绝
     */
    public static BigDecimal of(BigDecimal actualQuantity, BigDecimal orderedQuantity) {
        if (actualQuantity != null && actualQuantity.signum() > 0) {
            return actualQuantity;
        }
        return orderedQuantity;
    }
}
