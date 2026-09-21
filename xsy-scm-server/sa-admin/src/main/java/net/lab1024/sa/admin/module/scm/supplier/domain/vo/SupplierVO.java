package net.lab1024.sa.admin.module.scm.supplier.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 供应商列表行。
 */
@Data
public class SupplierVO {

    private Long supplierId;

    private Integer version;

    private String supplierCode;

    private String name;

    private String status;

    private String contactName;

    private String contactPhone;

    /**
     * 关联商品数，批量补全（不 N+1）。
     */
    private Long skuCount;

    private OffsetDateTime updatedAt;
}
