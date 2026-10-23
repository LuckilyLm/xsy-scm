package com.xsy.scm.delivery.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "xsy_delivery_route", autoResultMap = true)
public class DeliveryRouteEntity extends DeliveryRecord {
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String routeNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String routeName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate deliveryDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String warehouseNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String warehouseAddressSnapshot;
    @JsonSerialize(nullsUsing = NullSerializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal startLongitude;
    @JsonSerialize(nullsUsing = NullSerializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal startLatitude;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String startGeomCrs;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long driverId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String driverNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String driverPhoneSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long vehicleId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String vehicleNoSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime plannedDepartureTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long outboundId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cancelReason;
    /**
     * 发车时间和操作人与发车状态在同一事务中保存。后续普通表单更新不能清空这些已确认的发车事实。
     */
    private OffsetDateTime dispatchedAt;
    private String dispatchedBy;
    /**
     * 线路完成时刻与操作人；要求全部活动订单已进入 SIGNED / EXCEPTION。
     */
    private OffsetDateTime completedAt;
    private String completedBy;
}
