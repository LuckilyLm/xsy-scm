package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 销售订单来源，与订单表的 {@code order_source} 取值对应。
 *
 * <p>
 * {@code desc} 供导出与页面展示使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderSourceEnum {

    /**
     * 后台建单。
     */
    ADMIN("后台"),

    /**
     * 客户商城下单。
     */
    MALL("商城"),

    /**
     * 补录。
     */
    SUPPLEMENT("补录"),

    /**
     * 批量导入。
     */
    IMPORT("导入");

    private final String desc;

    /**
     * 按枚举名取中文；不认识的值原样返回，避免导出出现空白。
     */
    public static String descOf(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        for (ScmOrderSourceEnum item : values()) {
            if (item.name().equals(name)) {
                return item.desc;
            }
        }
        return name;
    }
}
