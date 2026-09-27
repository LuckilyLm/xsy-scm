package com.xsy.scm.order.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;

@Data
@TableName(value = "order_address_snapshot", autoResultMap = true)
public class OrderAddressSnapshotEntity {
    private Integer provinceCode;
    private String provinceName;
    private Integer cityCode;
    private String cityName;
    private Integer districtCode;
    private String districtName;
    @JsonSerialize(nullsUsing = NullSerializer.class)
    private BigDecimal longitude;
    @JsonSerialize(nullsUsing = NullSerializer.class)
    private BigDecimal latitude;
    private String geomCrs;

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String receiverName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String receiverPhone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;
}
