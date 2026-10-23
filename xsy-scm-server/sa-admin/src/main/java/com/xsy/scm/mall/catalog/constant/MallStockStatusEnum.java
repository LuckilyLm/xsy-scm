package com.xsy.scm.mall.catalog.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商城配送仓下按销售单位展示的可售库存状态。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum MallStockStatusEnum {

    /**
     * 有货。
     */
    AVAILABLE("有货"),

    /**
     * 缺货。
     */
    OUT_OF_STOCK("缺货"),

    /**
     * 库存不足：有货但少于购买量。
     */
    INSUFFICIENT("库存不足"),

    /**
     * 未知：库存服务不可用或未绑定配送仓。
     */
    UNKNOWN("未知");

    private final String desc;
}
