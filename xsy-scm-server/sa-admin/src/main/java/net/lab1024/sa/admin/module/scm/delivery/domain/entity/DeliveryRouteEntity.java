package net.lab1024.sa.admin.module.scm.delivery.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.*;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "delivery_route", autoResultMap = true)
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
}
