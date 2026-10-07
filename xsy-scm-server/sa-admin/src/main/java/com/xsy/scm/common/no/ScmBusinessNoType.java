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
    WAREHOUSE("WH", "warehouse_code_seq");

    private final String prefix;

    private final String sequence;
}
