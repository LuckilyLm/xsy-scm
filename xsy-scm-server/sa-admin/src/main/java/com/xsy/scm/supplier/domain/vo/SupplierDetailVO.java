package com.xsy.scm.supplier.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 供应商详情。
 */
@Data
public class SupplierDetailVO {

    private Long supplierId;

    private Integer version;

    private String supplierCode;

    private String name;

    private String status;

    private Integer paymentPeriodDays;

    private String contactName;

    private String contactPhone;

    private String address;

    private Integer provinceCode;

    private String provinceName;

    private Integer cityCode;

    private String cityName;

    private Integer districtCode;

    private String districtName;

    /**
     * 点位经度 / 纬度 / 坐标系；三列同生同灭。
     *
     * <p>
     * 经度与纬度显式声明 {@code null} 序列化：前端要靠「字段存在且为 null」区分「尚未采集点位」，
     * 省略字段会被读成「这个字段不存在」。
     */
    @JsonSerialize(nullsUsing = NullSerializer.class)
    private BigDecimal longitude;

    @JsonSerialize(nullsUsing = NullSerializer.class)
    private BigDecimal latitude;

    private String geomCrs;

    private String remark;

    private Long skuCount;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
