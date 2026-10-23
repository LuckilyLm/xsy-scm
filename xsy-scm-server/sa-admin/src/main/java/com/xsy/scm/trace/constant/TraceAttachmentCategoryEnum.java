package com.xsy.scm.trace.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 溯源附件的类别。
 *
 * <p>
 * 商品资质挂在商品上，检测报告挂在批次上。扫码页合并展示，但保存时 biz_type 不同，不能共用一个枚举值。
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
