package net.lab1024.sa.admin.module.scm.supplier.domain.vo;

import lombok.Data;

/** 供应商关联商品数投影，用于列表批量补全 {@code skuCount}（一次查询，不 N+1）。 */
@Data
public class SupplierSkuCountVO {

    private Long supplierId;

    private Long skuCount;
}
