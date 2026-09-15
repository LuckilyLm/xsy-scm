package net.lab1024.sa.admin.module.scm.supplier.domain.vo;

import lombok.Data;

/** 供应商下拉选项。只返回 ENABLED（S11），不分页。 */
@Data
public class SupplierOptionVO {

    private Long supplierId;

    private String supplierCode;

    private String name;
}
