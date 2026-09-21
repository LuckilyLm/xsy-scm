package net.lab1024.sa.admin.module.scm.customer.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class CustomerSkuVisibilityReverseVO {
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String customerTypeName;
    private String visibilityPolicy;
    private Long skuId;
    private String skuCode;
    private String productName;
    private String specName;
    private String skuStatus;
    private String spuStatus;
    private OffsetDateTime createdAt;
    private String createdBy;
}
