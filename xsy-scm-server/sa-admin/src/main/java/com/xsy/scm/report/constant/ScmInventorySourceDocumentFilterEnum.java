package com.xsy.scm.report.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 库存流水报表可筛选的来源单据类型。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventorySourceDocumentFilterEnum {

    /**
     * 采购收货明细。
     */
    PURCHASE_RECEIPT_ITEM("采购收货"),

    /**
     * 销售出库明细。
     */
    SALES_OUTBOUND_ITEM("销售出库"),

    /**
     * 盘点明细。
     */
    STOCKTAKE_ITEM("盘点"),

    /**
     * 报损报溢明细。
     */
    LOSS_GAIN_ITEM("报损报溢"),

    /**
     * 调拨出库明细。
     */
    TRANSFER_OUT_ITEM("调拨出库"),

    /**
     * 调拨入库明细。
     */
    TRANSFER_IN_ITEM("调拨入库"),

    /**
     * 规格转换出库明细。
     */
    CONVERT_OUT_ITEM("转换出库"),

    /**
     * 规格转换入库明细。
     */
    CONVERT_IN_ITEM("转换入库");

    private final String desc;
}
