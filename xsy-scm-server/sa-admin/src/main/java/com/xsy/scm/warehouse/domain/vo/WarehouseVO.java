package com.xsy.scm.warehouse.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 仓库列表、下拉与详情共用的视图。
 */
@Data
public class WarehouseVO {
    @JsonSerialize(nullsUsing = NullSerializer.class)
    private BigDecimal longitude;
    @JsonSerialize(nullsUsing = NullSerializer.class)
    private BigDecimal latitude;
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
