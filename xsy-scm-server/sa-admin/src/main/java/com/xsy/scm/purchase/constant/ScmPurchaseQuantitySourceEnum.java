package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 入库数量的来源。标准品按申报数量入库，非标品按实际称重入库； 数量来源是系统或人工，与称重设备来源是不同概念。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPurchaseQuantitySourceEnum {

    /** 系统按规则计算。 */
    SYSTEM("系统计算"),

    /** 人工录入。 */
    MANUAL("人工录入");

    private final String desc;
}
