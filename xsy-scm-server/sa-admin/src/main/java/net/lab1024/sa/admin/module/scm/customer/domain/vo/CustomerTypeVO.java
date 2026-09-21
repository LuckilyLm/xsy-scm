package net.lab1024.sa.admin.module.scm.customer.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 客户类型（列表行与下拉共用）。
 */
@Data
public class CustomerTypeVO {

    private Long typeId;

    private Integer version;

    private String typeCode;

    private String name;

    private String status;

    private OffsetDateTime createdAt;
}
