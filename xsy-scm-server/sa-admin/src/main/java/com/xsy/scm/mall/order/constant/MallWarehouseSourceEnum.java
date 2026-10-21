package com.xsy.scm.mall.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商城配送仓选项来源：客户绑定优先，其次为区县映射。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum MallWarehouseSourceEnum {

    /** 客户绑定的配送仓。 */
    CUSTOMER("客户绑定"),

    /** 按区县映射的配送仓。 */
    DISTRICT("区县映射");

    private final String desc;
}
