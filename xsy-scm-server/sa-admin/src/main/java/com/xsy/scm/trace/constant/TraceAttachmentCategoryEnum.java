package com.xsy.scm.trace.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 溯源附件的类别。
 *
 * <p>
 * 两类附件挂在不同的业务对象上：商品资质随商品，检测报告随批次。扫码页把两类合并展示， 但落库时的 biz_type 不同，不能用同一个枚举值掩盖。
 */
@Getter
@RequiredArgsConstructor
public enum TraceAttachmentCategoryEnum {

    /**
     * 商品资质：营业执照、生产许可等，随商品长期有效。
     */
    QUALIFICATION("商品资质"),

    /**
     * 批次检测报告：针对某一批到货的检验结论。
     */
    INSPECTION("检测报告");

    private final String desc;
}
