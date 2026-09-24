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
@TableName(value = "delivery_route_order", autoResultMap = true)
public class DeliveryRouteOrderEntity extends DeliveryRecord {
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long routeId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long stopId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNoSnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal orderAmountSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime expectDeliveryTimeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String assignmentStatus;
    /**
     * 生成打印预览 / 打印任务的历史次数；不代表物理出纸，也不代表当前内容版本已打印。
     */
    private Integer printCount;
    /**
     * 最后一次生成打印的时间；{@code null} 表示从未生成。
     */
    private OffsetDateTime lastPrintedAt;
    private String lastPrintedBy;
    /**
     * 订单级履约状态：{@code PENDING → IN_TRANSIT → SIGNED | EXCEPTION}。
     *
     * <p>与 {@code assignmentStatus} 正交：一个说「货到没到手」，一个说「这单还在不在线路上」。
     * 不拿 RELEASED 表达签收 —— 取消线路也会写 RELEASED，那与拒收不是同一个事实。
     */
    private String fulfillmentStatus;
    /**
     * 签收或异常登记时点，服务端写入；终态必填（DB CHECK）。
     */
    private OffsetDateTime signedAt;
    private String signedBy;
    /**
     * 异常签收必填原因；正常签收可留空备注。
     */
    private String signReason;
}
