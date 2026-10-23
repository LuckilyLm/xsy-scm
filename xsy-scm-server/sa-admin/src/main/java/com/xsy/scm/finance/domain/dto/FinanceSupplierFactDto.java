package com.xsy.scm.finance.domain.dto;

import lombok.Data;

/**
 * 供应商付款所需信息只读取得。供应商停用后仍可结清旧款；本对象不承担采购员归属判定。
 */
@Data
public class FinanceSupplierFactDto {

    private Long supplierId;

    /**
     * {@code xsy_supplier.name}，登记时冻结为 {@code counterparty_name_snapshot}。
     */
    private String supplierName;
}
