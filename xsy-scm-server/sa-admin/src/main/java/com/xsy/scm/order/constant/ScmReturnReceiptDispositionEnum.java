package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 退货收货后的处置方式。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmReturnReceiptDispositionEnum {

    /**
     * 退回库存：仍可销售，回到可用库存。
     */
    RETURN_TO_STOCK("退回库存"),

    /**
     * 报损：不可再售，按报损处理。
     */
    DAMAGE("报损");

    private final String desc;
}
