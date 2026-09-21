package net.lab1024.sa.admin.module.scm.warehouse.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/** 仓库（列表行 / 下拉 / 详情共用，W5 Target Design §7.2）。 */
@Data
public class WarehouseVO {
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(nullsUsing=com.fasterxml.jackson.databind.ser.std.NullSerializer.class)
    private java.math.BigDecimal longitude;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(nullsUsing=com.fasterxml.jackson.databind.ser.std.NullSerializer.class)
    private java.math.BigDecimal latitude;
    private String geomCrs;


    private Long id;

    private String warehouseCode;

    private String name;

    private String status;

    private String address;

    private Integer provinceCode;

    private String provinceName;

    private Integer cityCode;

    private String cityName;

    private Integer districtCode;

    private String districtName;

    private String remark;

    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
