package net.lab1024.sa.admin.module.scm.supplier.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/** 供应商详情。 */
@Data
public class SupplierDetailVO {

    private Long supplierId;

    private Integer version;

    private String supplierCode;

    private String name;

    private String status;

    private String contactName;

    private String contactPhone;

    private String address;

    private String remark;

    private Long skuCount;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
