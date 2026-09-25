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
    /**
     * 发车时刻与操作人；由 dispatch 与 status 同事务写入。
     *
     * <p>刻意不标 {@code ALWAYS}：本实体的多数列用 ALWAYS 是为了让「表单整行替换」能把值清空，
     * 而这四列不是表单字段 —— 一旦被某次不带值的整行更新抹掉，线路上就出现
     * 「状态是 DISPATCHED 却没有发车时点」，而库里的 CHECK 会直接把那次更新打回（V63）。
     */
    private OffsetDateTime dispatchedAt;
    private String dispatchedBy;
    /**
     * 线路完成时刻与操作人；要求全部活动订单已进入 SIGNED / EXCEPTION。
     */
    private OffsetDateTime completedAt;
    private String completedBy;
}
