package com.xsy.scm.common.no;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * SCM 内部编码类型：前缀 + 计数序列名。
 *
 * <p>
 * 只登记由后端生成的内部编码。商品条码、车牌号这类外部真实标识不在此列，它们仍由人工录入。
 */
@Getter
@RequiredArgsConstructor
public enum ScmBusinessNoType {

    CUSTOMER("CUS", "customer_code_seq"),
    SUPPLIER("SUP", "supplier_code_seq"),
    WAREHOUSE("WH", "warehouse_code_seq"),
    PRODUCT_SPU("SPU", "product_spu_code_seq"),
    PRODUCT_SKU("SKU", "product_sku_code_seq"),
    PRODUCT_CATEGORY("CAT", "product_category_code_seq"),
    PRODUCT_TAG("TAG", "product_tag_code_seq"),
    /**
     * 追溯批次：批次有业务日期语义，按「前缀 + 业务日 + 序号」发号。
     */
    TRACE_LOT("LOT", "trace_lot_no_seq");

    private final String prefix;

    private final String sequence;
}
