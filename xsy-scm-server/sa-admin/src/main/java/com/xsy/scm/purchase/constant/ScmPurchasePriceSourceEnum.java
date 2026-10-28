package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 采购价写入来源，落在 {@code xsy_purchase_order_item.price_fill_source} 与价格调整审计的 source 列上。
 *
 * <p>
 * 只有两种来路：人工补价/改价，或超期后按最近一次已提交采购价自动回填。数据库 CHECK 与这里保持同一组取值。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPurchasePriceSourceEnum {

    /** 采购负责人或财务手工补价、改价。 */
    MANUAL("人工补价/改价"),

    /** 提交满价格窗口仍无价，按最近一次已提交采购价回填。 */
    AUTO_LAST_PRICE("超期自动回填");

    private final String desc;
}
