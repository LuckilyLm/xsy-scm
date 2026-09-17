package net.lab1024.sa.admin.module.scm.warehouse.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/** 仓库（列表行 / 下拉 / 详情共用，W5 Target Design §7.2）。 */
@Data
public class WarehouseVO {

    private Long id;

    private String warehouseCode;

    private String name;

    private String status;

    private String address;

    private String remark;

    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
