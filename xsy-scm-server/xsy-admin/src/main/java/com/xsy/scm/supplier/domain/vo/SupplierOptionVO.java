package com.xsy.scm.supplier.domain.vo;

import lombok.Data;

/**
 * 供应商下拉选项：只返回 ENABLED 记录，不分页。
 */
@Data
public class SupplierOptionVO {

    private Long supplierId;

    private String supplierCode;

    private String name;
}
